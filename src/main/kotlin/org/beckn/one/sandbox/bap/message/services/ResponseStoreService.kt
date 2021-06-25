package org.beckn.one.sandbox.bap.message.services

import arrow.core.Either
import org.beckn.one.sandbox.bap.client.services.SearchService
import org.beckn.one.sandbox.bap.errors.database.DatabaseError
import org.beckn.one.sandbox.bap.message.entities.BecknResponse
import org.beckn.one.sandbox.bap.message.mappers.GenericResponseMapper
import org.beckn.one.sandbox.bap.message.repositories.BecknResponseRepository
import org.beckn.one.sandbox.bap.schemas.ProtocolResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ResponseStoreService<Proto: ProtocolResponse, Entity: BecknResponse> @Autowired constructor(
  val responseRepo: BecknResponseRepository<Entity>,
  val mapper: GenericResponseMapper<Proto, Entity>
) {
  private val log: Logger = LoggerFactory.getLogger(SearchService::class.java)

  fun save(protoResponse: Proto): Either<DatabaseError.OnWrite, Proto> =
    Either
      .catch { responseRepo.insertOne(mapper.protocolToEntity(protoResponse)) }
      .bimap(
        rightOperation = { protoResponse },
        leftOperation = {
          log.error("Exception while saving search response", it)
          DatabaseError.OnWrite
        }
      )

  fun findByMessageId(id: String) = Either
    .catch { responseRepo.findByMessageId(id) }
    .map { toSchema(it) }
    .mapLeft { e ->
      log.error("Exception while fetching search response", e)
      DatabaseError.OnRead
    }

  private fun toSchema(allResponses: List<Entity>) =
    allResponses.mapNotNull { response ->
      if (response.error == null) mapper.entityToProtocol(response) else null
    }
}