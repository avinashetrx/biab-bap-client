package org.beckn.one.sandbox.bap.client.controllers

import org.beckn.one.sandbox.bap.client.dtos.DeliveryInfoDto
import org.beckn.one.sandbox.bap.client.dtos.OrderDto
import org.beckn.one.sandbox.bap.client.services.InitializeOrderService
import org.beckn.one.sandbox.bap.errors.HttpError
import org.beckn.one.sandbox.bap.schemas.*
import org.beckn.one.sandbox.bap.schemas.factories.ContextFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class InitializeOrderController @Autowired constructor(
  private val contextFactory: ContextFactory,
  private val initializeOrderService: InitializeOrderService
) {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  @PostMapping("/client/v1/initialize_order")
  @ResponseBody
  fun initializeOrder(
    @RequestBody order: OrderDto
  ): ResponseEntity<ProtocolAckResponse> {
    val context = getContext(order.transactionId)
    return initializeOrderService.initOrder(
      context = context,
      order = order,
      deliveryInfo = order.deliveryInfo,
      billingInfo = order.billingInfo
    )
      .fold(
        {
          log.error("Error when initializing order: {}", it)
          mapToErrorResponse(it, context)
        },
        {
          log.info("Successfully initialized order. Message: {}", it)
          ResponseEntity.ok(ProtocolAckResponse(context = context, message = ResponseMessage.ack()))
        }
      )
  }

  private fun mapToErrorResponse(it: HttpError, context: ProtocolContext) = ResponseEntity
    .status(it.status())
    .body(
      ProtocolAckResponse(
        context = context,
        message = it.message(),
        error = it.error()
      )
    )

  private fun getContext(transactionId: String) =
    contextFactory.create(action = ProtocolContext.Action.INIT, transactionId = transactionId)
}