/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.analysis;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import org.camunda.optimize.rest.queryparam.QueryParamUtil;

import javax.ws.rs.QueryParam;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
public class ProcessDefinitionParametersRequestDto extends ProcessDefinitionParametersDto {
  @Override
  @QueryParam("processDefinitionKey")
  public void setProcessDefinitionKey(final String processDefinitionKey) {
    super.setProcessDefinitionKey(processDefinitionKey);
  }

  @Override
  @QueryParam("processDefinitionVersions")
  public void setProcessDefinitionVersions(final List<String> processDefinitionVersions) {
    super.setProcessDefinitionVersions(processDefinitionVersions);
  }

  @Override
  @QueryParam("tenantIds")
  public void setTenantIds(List<String> tenantIds) {
    this.tenantIds = normalizeNullTenants(tenantIds);
  }

}
