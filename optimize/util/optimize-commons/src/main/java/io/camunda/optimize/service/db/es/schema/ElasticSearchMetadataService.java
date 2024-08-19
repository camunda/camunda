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
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.MetadataIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchMetadataService
    extends DatabaseMetadataService<OptimizeElasticsearchClient> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ElasticSearchMetadataService.class);

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

      final GetResponse getMetadataResponse =
          esClient.get(new GetRequest(METADATA_INDEX_NAME, MetadataIndex.ID));
      if (!getMetadataResponse.isExists()) {
        log.warn(
            "Optimize Metadata index exists but no metadata doc was found, thus no metadata available.");
        return Optional.empty();
      }
      return Optional.ofNullable(
          objectMapper.readValue(getMetadataResponse.getSourceAsString(), MetadataDto.class));
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
    final Script updateScript =
        new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            scriptData.scriptString(),
            scriptData.params());
    try {
      final UpdateRequest request =
          new UpdateRequest()
              .index(METADATA_INDEX_NAME)
              .id(MetadataIndex.ID)
              .script(updateScript)
              .upsert(objectMapper.writeValueAsString(newMetadataIfAbsent), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse response = esClient.update(request);
      if (!response.getResult().equals(DocWriteResponse.Result.CREATED)
          && !response.getResult().equals(DocWriteResponse.Result.UPDATED)) {
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
