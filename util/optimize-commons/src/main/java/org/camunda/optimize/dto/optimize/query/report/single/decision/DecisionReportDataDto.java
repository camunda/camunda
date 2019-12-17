/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
public class DecisionReportDataDto extends SingleReportDataDto implements Combinable {

  protected String decisionDefinitionKey;
  protected List<String> decisionDefinitionVersions = new ArrayList<>();
  protected String decisionDefinitionName;
  protected List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  protected List<DecisionFilterDto> filter = new ArrayList<>();
  protected DecisionViewDto view;
  protected DecisionGroupByDto groupBy;
  protected DecisionVisualization visualization;

  @JsonIgnore
  @Override
  public String getDefinitionKey() {
    return decisionDefinitionKey;
  }

  @JsonIgnore
  @Override
  public List<String> getDefinitionVersions() {
    return decisionDefinitionVersions;
  }

  @JsonIgnore
  @Override
  public String getDefinitionName() {
    return decisionDefinitionName;
  }

  @JsonIgnore
  public void setDecisionDefinitionVersion(String definitionVersion) {
    this.decisionDefinitionVersions = Lists.newArrayList(definitionVersion);
  }

  @JsonIgnore
  @Override
  public String createCommandKey() {
    String viewCommandKey = view == null ? "null" : view.createCommandKey();
    String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    return viewCommandKey + "_" + groupByCommandKey;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionReportDataDto)) {
      return false;
    }
    DecisionReportDataDto that = (DecisionReportDataDto) o;
    return Combinable.isCombinable(view, that.view) &&
      Combinable.isCombinable(groupBy, that.groupBy) &&
      Objects.equals(visualization, that.visualization);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

}
