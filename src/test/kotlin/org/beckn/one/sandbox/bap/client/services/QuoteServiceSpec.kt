package org.beckn.one.sandbox.bap.client.services

import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import org.beckn.one.sandbox.bap.client.dtos.CartDto
import org.beckn.one.sandbox.bap.client.errors.CartError
import org.beckn.one.sandbox.bap.client.factories.CartFactory
import org.beckn.one.sandbox.bap.client.mappers.SelectedItemMapperImpl
import org.beckn.one.sandbox.bap.common.factories.ContextFactoryInstance
import org.beckn.one.sandbox.bap.message.services.MessageService
import org.mockito.Mockito.mock
import org.mockito.kotlin.verifyNoMoreInteractions

class QuoteServiceSpec : DescribeSpec() {
  private val messageService = mock(MessageService::class.java)
  private val registryService = mock(RegistryService::class.java)
  private val bppService = mock(BppService::class.java)
  private val context = ContextFactoryInstance.create().create()
  private val quoteService = QuoteService(
    messageService = messageService,
    registryService = registryService,
    bppService = bppService,
    selectedItemMapper = SelectedItemMapperImpl()
  )

  init {
    describe("Get quote") {

      it("should return success with null message when cart is empty") {
        val quote = quoteService.getQuote(
          context = context,
          cart = CartDto(items = listOf())
        )

        quote shouldBeRight null
        verifyNoMoreInteractions(registryService)
        verifyNoMoreInteractions(bppService)
        verifyNoMoreInteractions(messageService)
      }

      it("should return error when multiple BPP items are part of the cart") {
        val cartWithMultipleBppItems = CartFactory.create(bpp1Uri = "www.bpp1.com", bpp2Uri = "www.bpp2.com")

        val quote = quoteService.getQuote(
          context = context,
          cart = cartWithMultipleBppItems
        )

        quote shouldBeLeft CartError.MultipleBpps
        verifyNoMoreInteractions(registryService)
        verifyNoMoreInteractions(bppService)
        verifyNoMoreInteractions(messageService)
      }

      it("should return error when multiple Provider items are part of the cart") {
        val cartWithMultipleProviderItems =
          CartFactory.create(
            bpp1Uri = "www.bpp1.com",
            provider2Id = "padma coffee works",
            provider2Location = listOf("padma coffee works location 1")
          )

        val quote = quoteService.getQuote(
          context = context,
          cart = cartWithMultipleProviderItems
        )

        quote shouldBeLeft CartError.MultipleProviders
        verifyNoMoreInteractions(registryService)
        verifyNoMoreInteractions(bppService)
        verifyNoMoreInteractions(messageService)
      }
    }
  }
}