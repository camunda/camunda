/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.MetadataIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchMetadataService extends DatabaseMetadataService<OptimizeOpenSearchClient> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OpenSearchMetadataService.class);

  public OpenSearchMetadataService(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public Optional<MetadataDto> readMetadata(final OptimizeOpenSearchClient osClient) {
    final boolean metaDataIndexExists =
        osClient.getRichOpenSearchClient().index().indexExists(METADATA_INDEX_NAME);
    if (!metaDataIndexExists) {
      log.info("Optimize Metadata index wasn't found, thus no metadata available.");
      return Optional.empty();
    }
    try {
      return osClient
          .getRichOpenSearchClient()
          .doc()
          .getWithRetries(METADATA_INDEX_NAME, MetadataIndex.ID, MetadataDto.class);
    } catch (final OptimizeRuntimeException e) {
      log.error(ERROR_MESSAGE_READING_METADATA_DOC, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_READING_METADATA_DOC, e);
    }
  }

  @Override
  protected void upsertMetadataWithScript(
      final OptimizeOpenSearchClient osClient,
      final String schemaVersion,
      final String newInstallationId,
      final ScriptData updateScript) {
    readMetadata(osClient)
        .ifPresentOrElse(
            metadataDto -> {
              if (StringUtils.isBlank(metadataDto.getInstallationId())) {
                final UpdateRequest.Builder<Void, Void> updateRequestBuilder =
                    RequestDSL.<Void, Void>updateRequestBuilder(METADATA_INDEX_NAME)
                        .id(MetadataIndex.ID)
                        .script(QueryDSL.script(updateScript.scriptString(), updateScript.params()))
                        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

                final String errorMessage = "Error updating metadata information";

                final UpdateResponse<Void> response =
                    osClient.update(updateRequestBuilder, errorMessage);
                if (!response.result().equals(Result.Updated)) {
                  final String errorMsg = "Error doing metadata update. " + ERROR_MESSAGE_REQUEST;
                  log.error(errorMsg);
                  throw new OptimizeRuntimeException(errorMsg);
                }
              }
            },
            () -> {
              final MetadataDto newMetadataIfAbsent =
                  new MetadataDto(schemaVersion, newInstallationId);

              final UpdateRequest.Builder<Void, MetadataDto> requestBuilder =
                  new UpdateRequest.Builder<>();
              requestBuilder
                  .index(METADATA_INDEX_NAME)
                  .id(MetadataIndex.ID)
                  .docAsUpsert(true)
                  .doc(newMetadataIfAbsent)
                  .refresh(Refresh.True)
                  .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
              final String errorMessage = "Error performing the metadata creation";
              final UpdateResponse<Void> response = osClient.update(requestBuilder, errorMessage);

              if (!response.result().equals(Result.Created)) {
                final String errorMsg = "Error doing metadata creation. " + ERROR_MESSAGE_REQUEST;
                log.error(errorMsg);
                throw new OptimizeRuntimeException(errorMsg);
              }
            });
  }
}
