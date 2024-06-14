/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.writer.TenantWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class TenantWriterOS implements TenantWriter {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public void writeTenants(final List<TenantDto> tenantDtos) {
    String importItemName = "tenants";
    log.debug("Writing [{}] {} to Opensearch.", tenantDtos.size(), importItemName);

    osClient.doImportBulkRequestWithList(
        importItemName,
        tenantDtos,
        this::addImportTenantRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached(),
        TENANT_INDEX_NAME);
  }

  private BulkOperation addImportTenantRequest(TenantDto tenantDto) {
    final String id = tenantDto.getId();
    final Script updateScript =
        OpenSearchWriterUtil.createFieldUpdateScript(FIELDS_TO_UPDATE, tenantDto, objectMapper);

    final UpdateOperation<TenantDto> request =
        new UpdateOperation.Builder<TenantDto>()
            .id(id)
            .script(updateScript)
            .upsert(tenantDto)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
            .build();

    return new BulkOperation.Builder().update(request).build();
  }
}
