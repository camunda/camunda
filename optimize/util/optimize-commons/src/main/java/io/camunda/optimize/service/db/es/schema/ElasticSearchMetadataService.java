/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.MetadataIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchMetadataService
    extends DatabaseMetadataService<OptimizeElasticsearchClient> {

  public ElasticSearchMetadataService(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public Optional<MetadataDto> readMetadata(final OptimizeElasticsearchClient esClient) {
    try {
      final boolean metaDataIndexExists = esClient.exists(METADATA_INDEX_NAME);
      if (!metaDataIndexExists) {
        log.info("Optimize Metadata index wasn't found, thus no metadata available.");
        return Optional.empty();
      }

      final GetResponse<MetadataDto> getMetadataResponse =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  b -> b.optimizeIndex(esClient, METADATA_INDEX_NAME).id(MetadataIndex.ID)),
              MetadataDto.class);
      if (getMetadataResponse.source() == null) {
        log.warn(
            "Optimize Metadata index exists but no metadata doc was found, thus no metadata available.");
        return Optional.empty();
      }
      return Optional.of(getMetadataResponse.source());
    } catch (final IOException | ElasticsearchException e) {
      log.error(ERROR_MESSAGE_READING_METADATA_DOC, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_READING_METADATA_DOC, e);
    }
  }

  @Override
  protected void upsertMetadataWithScript(
      final OptimizeElasticsearchClient esClient,
      final String schemaVersion,
      final String newInstallationId,
      final ScriptData scriptData) {
    final MetadataDto newMetadataIfAbsent = new MetadataDto(schemaVersion, newInstallationId);
    try {
      final UpdateRequest<MetadataDto, ?> request =
          OptimizeUpdateRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, METADATA_INDEX_NAME)
                      .id(MetadataIndex.ID)
                      .script(
                          ib ->
                              ib.lang(ScriptLanguage.Painless)
                                  .source(scriptData.scriptString())
                                  .params(
                                      scriptData.params().entrySet().stream()
                                          .collect(
                                              Collectors.toMap(
                                                  Map.Entry::getKey,
                                                  e -> JsonData.of(e.getValue())))))
                      .upsert(newMetadataIfAbsent)
                      .refresh(Refresh.True)
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT));

      final UpdateResponse<?> response = esClient.update(request, MetadataDto.class);
      if (!response.result().equals(Result.Created) && !response.result().equals(Result.Updated)) {
        final String errorMsg =
            "Metadata information was neither created nor updated. " + ERROR_MESSAGE_REQUEST;
        log.error(errorMsg);
        throw new OptimizeRuntimeException(errorMsg);
      }
    } catch (final IOException e) {
      log.error(ERROR_MESSAGE_REQUEST, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_REQUEST, e);
    }
  }
}
