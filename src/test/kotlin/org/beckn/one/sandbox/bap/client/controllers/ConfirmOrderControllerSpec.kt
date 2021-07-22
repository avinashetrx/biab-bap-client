package org.beckn.one.sandbox.bap.client.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.beckn.one.sandbox.bap.client.dtos.ClientContext
import org.beckn.one.sandbox.bap.client.dtos.OrderPayment
import org.beckn.one.sandbox.bap.client.dtos.OrderRequestDto
import org.beckn.one.sandbox.bap.client.external.domains.Subscriber
import org.beckn.one.sandbox.bap.client.external.registry.SubscriberDto
import org.beckn.one.sandbox.bap.client.external.registry.SubscriberLookupRequest
import org.beckn.one.sandbox.bap.client.factories.OrderDtoFactory
import org.beckn.one.sandbox.bap.common.City
import org.beckn.one.sandbox.bap.common.Country
import org.beckn.one.sandbox.bap.common.Domain
import org.beckn.one.sandbox.bap.common.factories.MockNetwork
import org.beckn.one.sandbox.bap.common.factories.MockNetwork.anotherRetailBengaluruBpp
import org.beckn.one.sandbox.bap.common.factories.MockNetwork.registryBppLookupApi
import org.beckn.one.sandbox.bap.common.factories.MockNetwork.retailBengaluruBpp
import org.beckn.one.sandbox.bap.common.factories.ResponseFactory
import org.beckn.one.sandbox.bap.common.factories.SubscriberDtoFactory
import org.beckn.one.sandbox.bap.message.entities.MessageDao
import org.beckn.one.sandbox.bap.message.repositories.GenericRepository
import org.beckn.one.sandbox.bap.schemas.factories.ContextFactory
import org.beckn.one.sandbox.bap.schemas.factories.UuidFactory
import org.beckn.protocol.schemas.*
import org.litote.kmongo.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(value = ["test"])
@TestPropertySource(locations = ["/application-test.yml"])
class ConfirmOrderControllerSpec @Autowired constructor(
  val mockMvc: MockMvc,
  val objectMapper: ObjectMapper,
  val contextFactory: ContextFactory,
  val uuidFactory: UuidFactory,
  val messageRepository: GenericRepository<MessageDao>
) : DescribeSpec() {
  init {
    describe("Confirm order with BPP") {
      MockNetwork.startAllSubscribers()
      val context = ClientContext(transactionId = uuidFactory.create())
      val orderRequest = OrderRequestDto(
        message = OrderDtoFactory.create(
          bpp1_id = retailBengaluruBpp.baseUrl(),
          provider1_id = "padma coffee works",
          payment = OrderPayment(100.0, status = OrderPayment.Status.PAID, transactionId = "abc")
        ), context = context
      )

      beforeEach {
        MockNetwork.resetAllSubscribers()
        stubBppLookupApi(registryBppLookupApi, retailBengaluruBpp)
        stubBppLookupApi(registryBppLookupApi, anotherRetailBengaluruBpp)
      }

      it("should return error when BPP confirm call fails") {
        retailBengaluruBpp.stubFor(post("/confirm").willReturn(serverError()))

        val confirmOrderResponseString =
          invokeConfirmOrder(orderRequest).andExpect(MockMvcResultMatchers.status().isInternalServerError)
            .andReturn().response.contentAsString

        val confirmOrderResponse =
          verifyConfirmResponseMessage(
            confirmOrderResponseString,
            orderRequest,
            ResponseMessage.nack(),
            ProtocolError("BAP_011", "BPP returned error")
          )
        verifyThatMessageWasNotPersisted(confirmOrderResponse)
        verifyThatBppConfirmApiWasInvoked(confirmOrderResponse, orderRequest, retailBengaluruBpp)
        verifyThatSubscriberLookupApiWasInvoked(registryBppLookupApi, retailBengaluruBpp)
      }

      it("should validate that order contains items from only one bpp") {

        val orderRequestWithMultipleBppItems =
          OrderRequestDto(
            context = context,
            message = OrderDtoFactory.create(
              null,
              bpp1_id = retailBengaluruBpp.baseUrl(),
              bpp2_id = anotherRetailBengaluruBpp.baseUrl(),
              provider1_id = "padma coffee works",
              payment = OrderPayment(100.0, status = OrderPayment.Status.NOTPAID, transactionId = "abc")
            )
          )

        val confirmOrderResponseString = invokeConfirmOrder(orderRequestWithMultipleBppItems)
          .andExpect(MockMvcResultMatchers.status().is4xxClientError)
          .andReturn()
          .response.contentAsString

        val confirmOrderResponse =
          verifyConfirmResponseMessage(
            confirmOrderResponseString,
            orderRequestWithMultipleBppItems,
            ResponseMessage.nack(),
            ProtocolError("BAP_014", "More than one BPP's item(s) selected/initialized")
          )
        verifyThatMessageWasNotPersisted(confirmOrderResponse)
        verifyThatBppConfirmApiWasNotInvoked(retailBengaluruBpp)
        verifyThatSubscriberLookupApiWasNotInvoked(registryBppLookupApi)
        verifyThatSubscriberLookupApiWasNotInvoked(anotherRetailBengaluruBpp)
      }

      it("should validate that order contains items from only one provider") {
        val orderRequestWithMultipleProviderItems = OrderRequestDto(
          context = context,
          message = OrderDtoFactory.create(
            null,
            bpp1_id = retailBengaluruBpp.baseUrl(),
            provider1_id = "padma coffee works",
            provider2_id = "Venugopal store",
            payment = OrderPayment(100.0, status = OrderPayment.Status.NOTPAID, transactionId = "abc")
          )
        )

        val confirmOrderResponseString = invokeConfirmOrder(orderRequestWithMultipleProviderItems)
          .andExpect(MockMvcResultMatchers.status().is4xxClientError)
          .andReturn()
          .response.contentAsString

        val confirmOrderResponse =
          verifyConfirmResponseMessage(
            confirmOrderResponseString,
            orderRequestWithMultipleProviderItems,
            ResponseMessage.nack(),
            ProtocolError("BAP_010", "More than one Provider's item(s) selected/initialized")
          )
        verifyThatMessageWasNotPersisted(confirmOrderResponse)
        verifyThatBppConfirmApiWasNotInvoked(retailBengaluruBpp)
        verifyThatSubscriberLookupApiWasNotInvoked(registryBppLookupApi)
        verifyThatSubscriberLookupApiWasNotInvoked(anotherRetailBengaluruBpp)
      }

      it("should validate if payment is done") {
        val orderRequestForTest = OrderRequestDto(
          message = OrderDtoFactory.create(
            bpp1_id = retailBengaluruBpp.baseUrl(),
            provider1_id = "padma coffee works",
            payment = OrderPayment(0.0, status = OrderPayment.Status.NOTPAID, transactionId = "abc")
          ), context = context
        )
        retailBengaluruBpp
          .stubFor(
            post("/confirm").willReturn(
              okJson(objectMapper.writeValueAsString(ResponseFactory.getDefault(contextFactory.create())))
            )
          )

        val confirmOrderResponseString = invokeConfirmOrder(orderRequestForTest)
          .andExpect(MockMvcResultMatchers.status().is4xxClientError)
          .andReturn()
          .response.contentAsString

        val confirmOrderResponse =
          verifyConfirmResponseMessage(
            confirmOrderResponseString, orderRequestForTest, ResponseMessage.nack(),
            ProtocolError("BAP_015", "BAP hasn't received payment yet")
          )
        verifyThatMessageWasNotPersisted(confirmOrderResponse)
        verifyThatBppConfirmApiWasNotInvoked(retailBengaluruBpp)
        verifyThatSubscriberLookupApiWasNotInvoked(registryBppLookupApi)
      }

      it("should return null when cart items are empty") {
        val orderRequestForTest = OrderRequestDto(
          message = OrderDtoFactory.create(
            bpp1_id = retailBengaluruBpp.baseUrl(),
            provider1_id = "padma coffee works",
            payment = OrderPayment(100.0, status = OrderPayment.Status.NOTPAID, transactionId = "abc"),
            items = emptyList()
          ), context = context
        )
        retailBengaluruBpp
          .stubFor(
            post("/confirm").willReturn(
              okJson(objectMapper.writeValueAsString(ResponseFactory.getDefault(contextFactory.create())))
            )
          )

        val confirmOrderResponseString = invokeConfirmOrder(orderRequestForTest)
          .andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
          .andReturn()
          .response.contentAsString

        val confirmOrderResponse =
          verifyConfirmResponseMessage(confirmOrderResponseString, orderRequestForTest, ResponseMessage.ack())
        verifyThatMessageWasNotPersisted(confirmOrderResponse)
        verifyThatBppConfirmApiWasNotInvoked(retailBengaluruBpp)
        verifyThatSubscriberLookupApiWasNotInvoked(registryBppLookupApi)
      }

      it("should invoke provider confirm api and save message when payment is done") {
        retailBengaluruBpp
          .stubFor(
            post("/confirm").willReturn(
              okJson(objectMapper.writeValueAsString(ResponseFactory.getDefault(contextFactory.create())))
            )
          )

        val confirmOrderResponseString = invokeConfirmOrder(orderRequest)
          .andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
          .andReturn()
          .response.contentAsString

        val confirmOrderResponse =
          verifyConfirmResponseMessage(confirmOrderResponseString, orderRequest, ResponseMessage.ack())
        verifyThatMessageWasPersisted(confirmOrderResponse)
        verifyThatBppConfirmApiWasInvoked(confirmOrderResponse, orderRequest, retailBengaluruBpp)
        verifyThatSubscriberLookupApiWasInvoked(registryBppLookupApi, retailBengaluruBpp)
      }

      registryBppLookupApi.stop()

    }
  }

  private fun verifyThatSubscriberLookupApiWasInvoked(
    registryBppLookupApi: WireMockServer,
    bppApi: WireMockServer
  ) {
    registryBppLookupApi.verify(
      postRequestedFor(urlEqualTo("/lookup"))
        .withRequestBody(
          equalToJson(
            objectMapper.writeValueAsString(
              SubscriberLookupRequest(
                subscriber_id = bppApi.baseUrl(),
                type = Subscriber.Type.BPP,
                domain = Domain.LocalRetail.value,
                country = Country.India.value,
                city = City.Bengaluru.value
              )
            )
          )
        )
    )
  }

  private fun verifyThatBppConfirmApiWasNotInvoked(bppApi: WireMockServer) =
    bppApi.verify(0, postRequestedFor(urlEqualTo("/confirm")))

  private fun verifyThatSubscriberLookupApiWasNotInvoked(registryBppLookupApi: WireMockServer) =
    registryBppLookupApi.verify(0, postRequestedFor(urlEqualTo("/lookup")))

  private fun stubBppLookupApi(
    registryBppLookupApi: WireMockServer,
    providerApi: WireMockServer
  ) {
    registryBppLookupApi
      .stubFor(
        post("/lookup")
          .withRequestBody(matchingJsonPath("$.subscriber_id", equalTo(providerApi.baseUrl())))
          .willReturn(okJson(getSubscriberForBpp(providerApi)))
      )
  }

  private fun getSubscriberForBpp(bppApi: WireMockServer) =
    objectMapper.writeValueAsString(
      listOf(
        SubscriberDtoFactory.getDefault(
          subscriber_id = bppApi.baseUrl(),
          baseUrl = bppApi.baseUrl(),
          type = SubscriberDto.Type.BPP,
        )
      )
    )

  private fun verifyConfirmResponseMessage(
    confirmOrderResponseString: String,
    orderRequest: OrderRequestDto,
    expectedMessage: ResponseMessage,
    expectedError: ProtocolError? = null
  ): ProtocolAckResponse {
    val confirmOrderResponse = objectMapper.readValue(confirmOrderResponseString, ProtocolAckResponse::class.java)
    confirmOrderResponse.context shouldNotBe null
    confirmOrderResponse.context?.messageId shouldNotBe null
    confirmOrderResponse.context?.transactionId shouldBe orderRequest.context.transactionId
    confirmOrderResponse.context?.action shouldBe ProtocolContext.Action.CONFIRM
    confirmOrderResponse.message shouldBe expectedMessage
    confirmOrderResponse.error shouldBe expectedError
    return confirmOrderResponse
  }

  private fun verifyThatBppConfirmApiWasInvoked(
    confirmOrderResponse: ProtocolAckResponse,
    orderRequest: OrderRequestDto,
    providerApi: WireMockServer
  ) {
    val protocolConfirmRequest = getProtocolConfirmRequest(confirmOrderResponse, orderRequest)
    providerApi.verify(
      postRequestedFor(urlEqualTo("/confirm"))
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(protocolConfirmRequest)))
    )
  }

  private fun getProtocolConfirmRequest(
    confirmOrderResponse: ProtocolAckResponse,
    orderRequest: OrderRequestDto
  ): ProtocolConfirmRequest {
    val locations =
      orderRequest.message.items?.first()?.provider?.locations?.map { ProtocolSelectMessageSelectedProviderLocations(id = it) }
    val provider =
      orderRequest.message.items?.first()?.provider// todo: does this hold good even for order object or is this gotten from somewhere else?
    return ProtocolConfirmRequest(
      context = confirmOrderResponse.context!!,
      message = ProtocolConfirmRequestMessage(
        order = ProtocolOrder(
          provider = ProtocolSelectMessageSelectedProvider(
            id = provider!!.id,
            locations = locations
          ),
          items = orderRequest.message.items!!.map {
            ProtocolSelectMessageSelectedItems(
              id = it.id,
              quantity = it.quantity
            )
          },
          billing = orderRequest.message.billingInfo,
          fulfillment = ProtocolFulfillment(
            end = ProtocolFulfillmentEnd(
              contact = ProtocolContact(
                phone = orderRequest.message.deliveryInfo.phone,
                email = orderRequest.message.deliveryInfo.email
              ), location = orderRequest.message.deliveryInfo.location
            ),
            type = "home_delivery",
            customer = ProtocolCustomer(person = ProtocolPerson(name = orderRequest.message.deliveryInfo.name))
          ),
          addOns = emptyList(),
          offers = emptyList(),
          payment = ProtocolPayment(
            params = mapOf("amount" to orderRequest.message.payment!!.paidAmount.toString()),
            status = ProtocolPayment.Status.PAID
          )
        )
      )
    )
  }

  private fun verifyThatMessageWasPersisted(confirmOrderResponse: ProtocolAckResponse) {
    val savedMessage = messageRepository.findOne(MessageDao::id eq confirmOrderResponse.context?.messageId)
    savedMessage shouldNotBe null
  }

  private fun verifyThatMessageWasNotPersisted(confirmOrderResponse: ProtocolAckResponse) {
    val savedMessage = messageRepository.findOne(MessageDao::id eq confirmOrderResponse.context?.messageId)
    savedMessage shouldBe null
  }

  private fun invokeConfirmOrder(orderRequest: OrderRequestDto) = mockMvc.perform(
    MockMvcRequestBuilders.post("/client/v1/confirm_order").header(
      org.springframework.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE
    ).content(objectMapper.writeValueAsString(orderRequest))
  )
}