/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.TenantWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class TenantWriterES implements TenantWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public void writeTenants(final List<TenantDto> tenantDtos) {
    final String importItemName = "tenants";
    log.debug("Writing [{}] {} to ES.", tenantDtos.size(), importItemName);
    esClient.doImportBulkRequestWithList(
        importItemName,
        tenantDtos,
        this::addImportTenantRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addImportTenantRequest(final BulkRequest bulkRequest, final TenantDto tenantDto) {
    final String id = tenantDto.getId();
    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(FIELDS_TO_UPDATE, tenantDto, objectMapper);
    @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST) final UpdateRequest request =
        new UpdateRequest()
            .index(TENANT_INDEX_NAME)
            .id(id)
            .script(updateScript)
            .upsert(objectMapper.convertValue(tenantDto, Map.class))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }
}
