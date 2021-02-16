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
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.TenantListHandlingUtil;
import org.camunda.optimize.util.SuppressionConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
public class DecisionReportDataDto extends SingleReportDataDto implements Combinable {

  protected String decisionDefinitionKey;
  protected List<String> decisionDefinitionVersions = new ArrayList<>();
  protected String decisionDefinitionName;
  protected List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  protected List<DecisionFilterDto<?>> filter = new ArrayList<>();
  protected DecisionViewDto view;
  protected DecisionGroupByDto<?> groupBy;
  protected ProcessDistributedByDto<?> distributedBy = new NoneDistributedByDto();
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

  @Override
  public List<ViewProperty> getViewProperties() {
    return Collections.singletonList(view.getProperty());
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
      isGroupByCombinable(that) &&
      Objects.equals(visualization, that.visualization);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  private boolean isGroupByCombinable(final DecisionReportDataDto that) {
    if (Combinable.isCombinable(this.groupBy, that.groupBy)) {
      if (isGroupByDateVariableReport()) {
        return getConfiguration()
          .getGroupByDateVariableUnit()
          .equals(that.getConfiguration().getGroupByDateVariableUnit());
      } else if (isGroupByNumberVariableReport()) {
        return isBucketSizeCombinable(that);
      }
      return true;
    }
    return false;
  }

  private boolean isBucketSizeCombinable(final DecisionReportDataDto that) {
    return this.getConfiguration().getCustomBucket().isActive()
      && that.getConfiguration().getCustomBucket().isActive()
      && Objects.equals(
      this.getConfiguration().getCustomBucket().getBucketSize(),
      that.getConfiguration().getCustomBucket().getBucketSize()
    ) || isBucketSizeIrrelevant(this) && isBucketSizeIrrelevant(that);
  }

  private boolean isBucketSizeIrrelevant(final DecisionReportDataDto reportData) {
    // Bucket size settings for combined reports are not relevant if custom bucket config is
    // inactive or bucket size is null
    if (reportData.getConfiguration().getCustomBucket().isActive()) {
      return reportData.getConfiguration().getCustomBucket().getBucketSize() == null;
    }
    return true;
  }

  private boolean isGroupByDateVariableReport() {
    if (groupBy != null
      && (DecisionGroupByType.INPUT_VARIABLE.equals(groupBy.getType())
      || DecisionGroupByType.OUTPUT_VARIABLE.equals(groupBy.getType()))) {
      @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
      VariableType varType = ((DecisionGroupByDto<DecisionGroupByVariableValueDto>) groupBy).getValue().getType();
      return VariableType.DATE.equals(varType);
    }
    return false;
  }

  private boolean isGroupByNumberVariableReport() {
    if (groupBy != null
      && (DecisionGroupByType.INPUT_VARIABLE.equals(groupBy.getType())
      || DecisionGroupByType.OUTPUT_VARIABLE.equals(groupBy.getType()))) {
      @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
      VariableType varType = ((DecisionGroupByDto<DecisionGroupByVariableValueDto>) groupBy).getValue().getType();
      return VariableType.getNumericTypes().contains(varType);
    }
    return false;
  }

}
