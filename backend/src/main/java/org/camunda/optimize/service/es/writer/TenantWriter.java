/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class TenantWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(TenantDto.Fields.name.name());

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

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

  private void addImportTenantRequest(BulkRequest bulkRequest, OptimizeDto optimizeDto) {
    if (!(optimizeDto instanceof TenantDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    TenantDto tenantDto = (TenantDto) optimizeDto;

    final String id = tenantDto.getId();
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      tenantDto,
      objectMapper
    );
    @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
    final UpdateRequest request =
      new UpdateRequest()
        .index(TENANT_INDEX_NAME)
        .id(id)
        .script(updateScript)
        .upsert(objectMapper.convertValue(tenantDto, Map.class))
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

}
