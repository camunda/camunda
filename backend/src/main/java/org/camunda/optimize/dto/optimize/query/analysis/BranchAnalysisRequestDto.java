/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class BranchAnalysisRequestDto {
  private String end;
  private String gateway;
  private String processDefinitionKey;
  private List<String> processDefinitionVersions;
  private List<String> tenantIds = Collections.singletonList(null);

  private List<ProcessFilterDto<?>> filter = new ArrayList<>();

  @JsonIgnore
  public void setProcessDefinitionVersion(String definitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(definitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
