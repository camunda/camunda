/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.db.schema.DatabaseMetadataService;
import org.camunda.optimize.service.db.schema.index.MetadataIndex;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchMetadataService extends DatabaseMetadataService<OptimizeElasticsearchClient> {

  public ElasticSearchMetadataService(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  protected void upsertMetadataWithScript(final OptimizeElasticsearchClient esClient,
                                          final String schemaVersion,
                                          final String newInstallationId,
                                          final ScriptData scriptData) {
    final MetadataDto newMetadataIfAbsent = new MetadataDto(schemaVersion, newInstallationId);
    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      scriptData.scriptString(),
      scriptData.params()
    );
    try {
      final UpdateRequest request = new UpdateRequest()
        .index(METADATA_INDEX_NAME)
        .id(MetadataIndex.ID)
        .script(updateScript)
        .upsert(objectMapper.writeValueAsString(newMetadataIfAbsent), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse response = esClient.update(request);
      if (!response.getResult().equals(DocWriteResponse.Result.CREATED)
        && !response.getResult().equals(DocWriteResponse.Result.UPDATED)) {
        String errorMsg = "Metadata information was neither created nor updated. " + ERROR_MESSAGE_REQUEST;
        log.error(errorMsg);
        throw new OptimizeRuntimeException(errorMsg);
      }
    } catch (IOException e) {
      log.error(ERROR_MESSAGE_REQUEST, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_REQUEST, e);
    }
  }

  @Override
  public Optional<MetadataDto> readMetadata(final OptimizeElasticsearchClient esClient) {
    try {
      final boolean metaDataIndexExists = esClient.exists(METADATA_INDEX_NAME);
      if (!metaDataIndexExists) {
        log.info("Optimize Metadata index wasn't found, thus no metadata available.");
        return Optional.empty();
      }

      final GetResponse getMetadataResponse = esClient.get(new GetRequest(METADATA_INDEX_NAME, MetadataIndex.ID));
      if (!getMetadataResponse.isExists()) {
        log.warn("Optimize Metadata index exists but no metadata doc was found, thus no metadata available.");
        return Optional.empty();
      }
      return Optional.ofNullable(objectMapper.readValue(getMetadataResponse.getSourceAsString(), MetadataDto.class));
    } catch (IOException | ElasticsearchException e) {
      log.error(ERROR_MESSAGE_READING_METADATA_DOC, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_READING_METADATA_DOC, e);
    }
  }

}
