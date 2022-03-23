/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.analysis;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.List;

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

  @Override
  @QueryParam("minimumDeviationFromAvg")
  @DefaultValue("50")
  public void setMinimumDeviationFromAvg(final long minimumDeviationFromAvg) {
    this.minimumDeviationFromAvg = minimumDeviationFromAvg;
  }

  @Override
  @QueryParam("disconsiderAutomatedTasks")
  @DefaultValue("false")
  public void setDisconsiderAutomatedTasks(final boolean disconsiderAutomatedTasks) {
    this.disconsiderAutomatedTasks = disconsiderAutomatedTasks;
  }

}
