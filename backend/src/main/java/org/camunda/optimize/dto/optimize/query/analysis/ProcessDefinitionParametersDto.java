/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import lombok.Data;
import org.camunda.optimize.rest.queryparam.QueryParamUtil;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ProcessDefinitionParametersDto {
  protected static final List<String> DEFAULT_TENANT_IDS = Collections.singletonList(null);

  protected String processDefinitionKey;
  protected List<String> processDefinitionVersions;
  protected List<String> tenantIds = DEFAULT_TENANT_IDS;

  public void setTenantIds(List<String> tenantIds) {
    this.tenantIds = normalizeTenants(tenantIds);
  }

  private List<String> normalizeTenants(List<String> tenantIds) {
    final List<String> normalizedTenantIds = tenantIds.stream()
      .map(QueryParamUtil::normalizeNullStringValue)
      .collect(Collectors.toList());
    return normalizedTenantIds.isEmpty() ? DEFAULT_TENANT_IDS : normalizedTenantIds;
  }
}
