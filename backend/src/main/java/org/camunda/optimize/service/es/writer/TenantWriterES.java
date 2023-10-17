/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.db.writer.TenantWriter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;

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
    String importItemName = "tenants";
    log.debug("Writing [{}] {} to ES.", tenantDtos.size(), importItemName);
    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      tenantDtos,
      this::addImportTenantRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportTenantRequest(BulkRequest bulkRequest, TenantDto tenantDto) {
    final String id = tenantDto.getId();
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      tenantDto,
      objectMapper
    );
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
