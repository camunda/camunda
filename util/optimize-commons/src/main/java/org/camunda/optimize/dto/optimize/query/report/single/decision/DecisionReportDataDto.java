/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.validation.DecisionFiltersMustReferenceExistingDefinitionsConstraint;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@NoArgsConstructor
@SuperBuilder
@DecisionFiltersMustReferenceExistingDefinitionsConstraint
public class DecisionReportDataDto extends SingleReportDataDto {

  @Builder.Default
  @Valid
  protected List<DecisionFilterDto<?>> filter = new ArrayList<>();
  protected DecisionViewDto view;
  protected DecisionGroupByDto<?> groupBy;
  @Builder.Default
  protected ProcessReportDistributedByDto<?> distributedBy = new NoneDistributedByDto();
  protected DecisionVisualization visualization;

  public String getDecisionDefinitionKey() {
    return getDefinitionKey();
  }

  @JsonIgnore
  public void setDecisionDefinitionKey(final String key) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setKey(key);
  }

  @JsonIgnore
  public void setDecisionDefinitionName(String name) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setName(name);
  }

  public List<String> getDecisionDefinitionVersions() {
    return getDefinitionVersions();
  }

  @JsonIgnore
  public void setDecisionDefinitionVersions(final List<String> versions) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setVersions(versions);
  }

  @JsonIgnore
  public void setDecisionDefinitionVersion(final String version) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setVersion(version);
  }

  @Override
  public List<ViewProperty> getViewProperties() {
    return getView().getProperties();
  }

  @Override
  public List<String> createCommandKeys() {
    return Collections.singletonList(createCommandKey());
  }

  @JsonIgnore
  @Override
  public String createCommandKey() {
    String viewCommandKey = view == null ? "null" : view.createCommandKey();
    String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    return viewCommandKey + "_" + groupByCommandKey;
  }

}
