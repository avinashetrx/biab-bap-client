package org.beckn.one.sandbox.bap.message.factories

import org.beckn.one.sandbox.bap.message.entities.Category
import org.beckn.one.sandbox.bap.message.entities.ProviderCatalog
import org.beckn.one.sandbox.bap.schemas.ProtocolCategory
import org.beckn.one.sandbox.bap.schemas.ProtocolProviderCatalog

object ProtocolProviderCatalogFactory {

  fun create(index: Int) = ProtocolProviderCatalog(
    id = IdFactory.forProvider(index),
    descriptor = ProtocolDescriptorFactory.create("Retail-provider", IdFactory.forProvider(index)),
    categories = IdFactory.forCategory(IdFactory.forProvider(index), 1).map { ProtocolCategoryFactory.create(it) },
    items = IdFactory.forItems(IdFactory.forProvider(index), 1).map { ProtocolItemFactory.create(it) }
  )

  fun createAsEntity(protocol: ProtocolProviderCatalog) = ProviderCatalog(
    id = protocol.id,
    descriptor = ProtocolDescriptorFactory.createAsEntity(protocol.descriptor),
    categories = protocol.categories?.mapNotNull { ProtocolCategoryFactory.createAsEntity(it) },
    items = protocol.items?.map { ProtocolItemFactory.createAsEntity(it) },
  )
}

object ProtocolCategoryFactory{
  fun create(id: String) = ProtocolCategory(
    id = id,
    descriptor = ProtocolDescriptorFactory.create(id, id),
    tags = mapOf("category-tag1" to "category-value1")
  )

  fun createAsEntity(protocol: ProtocolCategory?) = protocol?.let {
    Category(
      id = protocol.id,
      descriptor = ProtocolDescriptorFactory.createAsEntity(protocol.descriptor),
      tags = protocol.tags
    )
  }
}