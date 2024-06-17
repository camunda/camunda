/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import static io.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.rest.queryparam.QueryParamUtil;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class ProcessDefinitionParametersDto {

  protected String processDefinitionKey;
  protected List<String> processDefinitionVersions;
  protected List<String> tenantIds = DEFAULT_TENANT_IDS;
  protected Long minimumDeviationFromAvg = 50L;
  protected Boolean disconsiderAutomatedTasks = false;
  protected List<ProcessFilterDto<?>> filters = new ArrayList<>();

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = normalizeTenants(tenantIds);
  }

  protected List<String> normalizeNullTenants(final List<String> tenantIds) {
    return tenantIds.stream()
        .map(QueryParamUtil::normalizeNullStringValue)
        .collect(Collectors.toList());
  }

  private List<String> normalizeTenants(final List<String> tenantIds) {
    final List<String> normalizedTenantIds = normalizeNullTenants(tenantIds);
    return normalizedTenantIds.isEmpty() ? DEFAULT_TENANT_IDS : normalizedTenantIds;
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
