/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_TYPE;

@AllArgsConstructor
@Component
@Slf4j
public class TenantWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(TenantDto.Fields.name.name());

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void writeTenants(final List<TenantDto> tenantDtos) {
    log.debug("Writing [{}] tenants to elasticsearch", tenantDtos.size());

    final BulkRequest bulkRequest = new BulkRequest();
    addUpsertsForEachDto(tenantDtos, bulkRequest);

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          final String errorMessage = String.format(
            "There were failures while writing tenants. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        log.error("There were errors while writing tenants.", e);
      }
    }
  }

  private void addUpsertsForEachDto(final List<TenantDto> tenantDtos, final BulkRequest bulkRequest) {
    for (TenantDto tenantDto : tenantDtos) {
      final String id = tenantDto.getId();
      final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(FIELDS_TO_UPDATE, tenantDto);
      final UpdateRequest request =
        new UpdateRequest(TENANT_TYPE, TENANT_TYPE, id)
          .script(updateScript)
          .upsert(objectMapper.convertValue(tenantDto, Map.class))
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      bulkRequest.add(request);
    }
  }

}
