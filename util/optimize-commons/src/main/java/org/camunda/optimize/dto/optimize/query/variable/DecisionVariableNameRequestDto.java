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
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class DecisionVariableNameRequestDto {
  @NotNull
  private String decisionDefinitionKey;
  private List<String> decisionDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(DEFAULT_TENANT_IDS);

  public DecisionVariableNameRequestDto(@NotNull final String key, final String version, final String tenantId) {
    this.decisionDefinitionKey = key;
    this.decisionDefinitionVersions = Collections.singletonList(version);
    this.tenantIds = Collections.singletonList(tenantId);
  }

  public DecisionVariableNameRequestDto(@NotNull final String key, final List<String> versions) {
    this.decisionDefinitionKey = key;
    this.decisionDefinitionVersions = versions;
  }

  @JsonIgnore
  public void setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.decisionDefinitionVersions = Lists.newArrayList(decisionDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
