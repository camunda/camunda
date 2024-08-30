/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;

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
