package org.beckn.one.sandbox.bap.client.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.beckn.one.sandbox.bap.client.dtos.*
import org.beckn.one.sandbox.bap.schemas.ProtocolAckResponse
import org.beckn.one.sandbox.bap.schemas.ProtocolScalar
import org.beckn.one.sandbox.bap.schemas.ResponseMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(value = ["test"])
@TestPropertySource(locations = ["/application-test.yml"])
class MockCartControllerSpec @Autowired constructor(
  val mockMvc: MockMvc,
  val objectMapper: ObjectMapper,
) : DescribeSpec() {
  init {
    describe("Cart") {

      it("should create cart") {
        val cart = getCart("cart 1")

        val createCartResponseString = mockMvc
          .perform(
            post("/client/v0/cart")
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .content(objectMapper.writeValueAsString(cart))
          )
          .andExpect(status().is2xxSuccessful)
          .andReturn()
          .response.contentAsString

        val createCartResponse = objectMapper.readValue(createCartResponseString, CreateCartResponseDto::class.java)
        createCartResponse.context shouldNotBe null
        createCartResponse.message.cart shouldBe cart
      }

      it("should return cart by id") {
        val cartId = "cart 1"

        val getCartResponseString = mockMvc
          .perform(
            get("/client/v0/cart/$cartId")
          )
          .andExpect(status().is2xxSuccessful)
          .andReturn()
          .response.contentAsString

        val getCartResponse = objectMapper.readValue(getCartResponseString, GetCartResponseDto::class.java)
        getCartResponse.message shouldBe CartResponseMessageDto(cart = getCart(cartId))
      }

      it("should update cart") {
        val cart = getCart("cart 1")

        val createCartResponseString = mockMvc
          .perform(
            put("/client/v0/cart/${cart.id}")
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .content(objectMapper.writeValueAsString(cart))
          )
          .andExpect(status().is2xxSuccessful)
          .andReturn()
          .response.contentAsString

        val createCartResponse = objectMapper.readValue(createCartResponseString, ProtocolAckResponse::class.java)
        createCartResponse.context shouldNotBe null
        createCartResponse.message.ack shouldNotBe ResponseMessage.ack()
      }

      it("should delete cart") {
        mockMvc
          .perform(
            delete("/client/v0/cart/cart-id")
          )
          .andExpect(status().is2xxSuccessful)
      }
    }
  }

  private fun getCart(cartId: String) = CartDto(
    id = cartId, items = listOf(
      CartItemDto(
        bppId = "paisool",
        provider = CartItemProviderDto(
          id = "venugopala stores",
          providerLocations = listOf("13.001581,77.5703686")
        ),
        itemId = "cothas-coffee-1",
        quantity = 2,
        measure = ProtocolScalar(
          value = BigDecimal.valueOf(500),
          unit = "gm"
        )
      ),
      CartItemDto(
        bppId = "paisool",
        provider = CartItemProviderDto(
          id = "maruthi-stores",
          providerLocations = listOf("12.9995218,77.5704439")
        ),
        itemId = "malgudi-coffee-500-gms",
        quantity = 1,
        measure = ProtocolScalar(
          value = BigDecimal.valueOf(1),
          unit = "kg"
        )
      )
    )
  )
}