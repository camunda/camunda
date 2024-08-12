/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.validation.DecisionFiltersMustReferenceExistingDefinitionsConstraint;
import io.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@SuperBuilder
@DecisionFiltersMustReferenceExistingDefinitionsConstraint
public class DecisionReportDataDto extends SingleReportDataDto {

  @Builder.Default @Valid protected List<DecisionFilterDto<?>> filter = new ArrayList<>();
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
  public void setDecisionDefinitionName(final String name) {
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

  @JsonIgnore
  @Override
  public String createCommandKey() {
    final String viewCommandKey = view == null ? "null" : view.createCommandKey();
    final String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    return viewCommandKey + "_" + groupByCommandKey;
  }

  @Override
  public List<String> createCommandKeys() {
    return Collections.singletonList(createCommandKey());
  }

  public static final class Fields {

    public static final String filter = "filter";
    public static final String view = "view";
    public static final String groupBy = "groupBy";
    public static final String distributedBy = "distributedBy";
    public static final String visualization = "visualization";
  }
}
