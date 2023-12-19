/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.schema.DatabaseMetadataService;
import org.camunda.optimize.service.db.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class OpenSearchMetadataService extends DatabaseMetadataService<OptimizeOpenSearchClient> {

  public OpenSearchMetadataService(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public Optional<MetadataDto> readMetadata(final OptimizeOpenSearchClient osClient) {
    final boolean metaDataIndexExists = osClient.getRichOpenSearchClient().index().indexExists(METADATA_INDEX_NAME);
    if (!metaDataIndexExists) {
      log.info("Optimize Metadata index wasn't found, thus no metadata available.");
      return Optional.empty();
    }

    return osClient.getRichOpenSearchClient().doc()
      .getWithRetries(METADATA_INDEX_NAME, MetadataIndex.ID, MetadataDto.class);
  }

  @Override
  protected void upsertMetadataWithScript(final OptimizeOpenSearchClient osClient,
                                          final String schemaVersion,
                                          final String newInstallationId,
                                          final ScriptData updateScript) {

    readMetadata(osClient).ifPresentOrElse(metadataDto -> {
      if (StringUtils.isBlank(metadataDto.getInstallationId())) {
        final UpdateRequest.Builder<Void, Void> updateRequestBuilder =
          RequestDSL.<Void, Void>updateRequestBuilder(METADATA_INDEX_NAME)
            .id(MetadataIndex.ID)
            .script(QueryDSL.script(updateScript.scriptString(), updateScript.params()))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

        final String errorMessage =  "Error updating metadata information";

        final UpdateResponse<Void> response = osClient.update(
          updateRequestBuilder,
          errorMessage
        );
        if (!response.result().equals(Result.Updated)) {
          String errorMsg = "Error doing metadata update. " + ERROR_MESSAGE_REQUEST;
          log.error(errorMsg);
          throw new OptimizeRuntimeException(errorMsg);
        }
      }
    }, () -> {
      final MetadataDto newMetadataIfAbsent = new MetadataDto(schemaVersion, newInstallationId);

      final UpdateRequest.Builder<Void, MetadataDto> requestBuilder = new UpdateRequest.Builder<>();
      requestBuilder
        .index(METADATA_INDEX_NAME)
        .id(MetadataIndex.ID)
        .docAsUpsert(true)
        .doc(newMetadataIfAbsent)
        .refresh(Refresh.True)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final String errorMessage =  "Error performing the metadata creation";

      final UpdateResponse<Void> response = osClient.update(
        requestBuilder,
        errorMessage
      );

      if (!response.result().equals(Result.Created)) {
        String errorMsg = "Error doing metadata creation. " + ERROR_MESSAGE_REQUEST;
        log.error(errorMsg);
        throw new OptimizeRuntimeException(errorMsg);
      }
    });
  }

}
