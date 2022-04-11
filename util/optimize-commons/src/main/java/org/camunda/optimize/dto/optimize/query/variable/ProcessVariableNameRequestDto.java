/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProcessVariableNameRequestDto {
  @NotNull
  private String processDefinitionKey;
  private List<String> processDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(DEFAULT_TENANT_IDS);

  @JsonIgnore
  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
