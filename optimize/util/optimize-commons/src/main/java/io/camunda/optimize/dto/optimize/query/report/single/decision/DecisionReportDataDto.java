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

@DecisionFiltersMustReferenceExistingDefinitionsConstraint
public class DecisionReportDataDto extends SingleReportDataDto {

  @Valid protected List<DecisionFilterDto<?>> filter = new ArrayList<>();
  protected DecisionViewDto view;
  protected DecisionGroupByDto<?> groupBy;

  protected ProcessReportDistributedByDto<?> distributedBy = new NoneDistributedByDto();

  protected DecisionVisualization visualization;

  public DecisionReportDataDto(
      @Valid final List<DecisionFilterDto<?>> filter,
      final DecisionViewDto view,
      final DecisionGroupByDto<?> groupBy,
      final ProcessReportDistributedByDto<?> distributedBy,
      final DecisionVisualization visualization) {
    this.filter = filter;
    this.view = view;
    this.groupBy = groupBy;
    this.distributedBy = distributedBy;
    this.visualization = visualization;
  }

  public DecisionReportDataDto() {}

  protected DecisionReportDataDto(final DecisionReportDataDtoBuilder<?, ?> b) {
    super(b);
    if (b.filterSet) {
      filter = b.filterValue;
    } else {
      filter = defaultFilter();
    }
    view = b.view;
    groupBy = b.groupBy;
    if (b.distributedBySet) {
      distributedBy = b.distributedByValue;
    } else {
      distributedBy = defaultDistributedBy();
    }
    visualization = b.visualization;
  }

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

  public @Valid List<DecisionFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(@Valid final List<DecisionFilterDto<?>> filter) {
    this.filter = filter;
  }

  public DecisionViewDto getView() {
    return view;
  }

  public void setView(final DecisionViewDto view) {
    this.view = view;
  }

  public DecisionGroupByDto<?> getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(final DecisionGroupByDto<?> groupBy) {
    this.groupBy = groupBy;
  }

  public ProcessReportDistributedByDto<?> getDistributedBy() {
    return distributedBy;
  }

  public void setDistributedBy(final ProcessReportDistributedByDto<?> distributedBy) {
    this.distributedBy = distributedBy;
  }

  public DecisionVisualization getVisualization() {
    return visualization;
  }

  public void setVisualization(final DecisionVisualization visualization) {
    this.visualization = visualization;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionReportDataDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DecisionReportDataDto(filter="
        + getFilter()
        + ", view="
        + getView()
        + ", groupBy="
        + getGroupBy()
        + ", distributedBy="
        + getDistributedBy()
        + ", visualization="
        + getVisualization()
        + ")";
  }

  @Valid
  private static List<DecisionFilterDto<?>> defaultFilter() {
    return new ArrayList<>();
  }

  private static ProcessReportDistributedByDto<?> defaultDistributedBy() {
    return new NoneDistributedByDto();
  }

  public static DecisionReportDataDtoBuilder<?, ?> builder() {
    return new DecisionReportDataDtoBuilderImpl();
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String filter = "filter";
    public static final String view = "view";
    public static final String groupBy = "groupBy";
    public static final String distributedBy = "distributedBy";
    public static final String visualization = "visualization";
  }

  public abstract static class DecisionReportDataDtoBuilder<
          C extends DecisionReportDataDto, B extends DecisionReportDataDtoBuilder<C, B>>
      extends SingleReportDataDtoBuilder<C, B> {

    private @Valid List<DecisionFilterDto<?>> filterValue;
    private boolean filterSet;
    private DecisionViewDto view;
    private DecisionGroupByDto<?> groupBy;
    private ProcessReportDistributedByDto<?> distributedByValue;
    private boolean distributedBySet;
    private DecisionVisualization visualization;

    public B filter(@Valid final List<DecisionFilterDto<?>> filter) {
      filterValue = filter;
      filterSet = true;
      return self();
    }

    public B view(final DecisionViewDto view) {
      this.view = view;
      return self();
    }

    public B groupBy(final DecisionGroupByDto<?> groupBy) {
      this.groupBy = groupBy;
      return self();
    }

    public B distributedBy(final ProcessReportDistributedByDto<?> distributedBy) {
      distributedByValue = distributedBy;
      distributedBySet = true;
      return self();
    }

    public B visualization(final DecisionVisualization visualization) {
      this.visualization = visualization;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "DecisionReportDataDto.DecisionReportDataDtoBuilder(super="
          + super.toString()
          + ", filterValue="
          + filterValue
          + ", view="
          + view
          + ", groupBy="
          + groupBy
          + ", distributedByValue="
          + distributedByValue
          + ", visualization="
          + visualization
          + ")";
    }
  }

  private static final class DecisionReportDataDtoBuilderImpl
      extends DecisionReportDataDtoBuilder<
          DecisionReportDataDto, DecisionReportDataDtoBuilderImpl> {

    private DecisionReportDataDtoBuilderImpl() {}

    @Override
    protected DecisionReportDataDtoBuilderImpl self() {
      return this;
    }

    @Override
    public DecisionReportDataDto build() {
      return new DecisionReportDataDto(this);
    }
  }
}
