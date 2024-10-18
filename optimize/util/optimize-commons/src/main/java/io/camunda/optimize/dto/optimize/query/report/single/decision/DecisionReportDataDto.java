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
    if (b.filter$set) {
      filter = b.filter$value;
    } else {
      filter = $default$filter();
    }
    view = b.view;
    groupBy = b.groupBy;
    if (b.distributedBy$set) {
      distributedBy = b.distributedBy$value;
    } else {
      distributedBy = $default$distributedBy();
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
    final int PRIME = 59;
    int result = 1;
    final Object $filter = getFilter();
    result = result * PRIME + ($filter == null ? 43 : $filter.hashCode());
    final Object $view = getView();
    result = result * PRIME + ($view == null ? 43 : $view.hashCode());
    final Object $groupBy = getGroupBy();
    result = result * PRIME + ($groupBy == null ? 43 : $groupBy.hashCode());
    final Object $distributedBy = getDistributedBy();
    result = result * PRIME + ($distributedBy == null ? 43 : $distributedBy.hashCode());
    final Object $visualization = getVisualization();
    result = result * PRIME + ($visualization == null ? 43 : $visualization.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionReportDataDto)) {
      return false;
    }
    final DecisionReportDataDto other = (DecisionReportDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$filter = getFilter();
    final Object other$filter = other.getFilter();
    if (this$filter == null ? other$filter != null : !this$filter.equals(other$filter)) {
      return false;
    }
    final Object this$view = getView();
    final Object other$view = other.getView();
    if (this$view == null ? other$view != null : !this$view.equals(other$view)) {
      return false;
    }
    final Object this$groupBy = getGroupBy();
    final Object other$groupBy = other.getGroupBy();
    if (this$groupBy == null ? other$groupBy != null : !this$groupBy.equals(other$groupBy)) {
      return false;
    }
    final Object this$distributedBy = getDistributedBy();
    final Object other$distributedBy = other.getDistributedBy();
    if (this$distributedBy == null
        ? other$distributedBy != null
        : !this$distributedBy.equals(other$distributedBy)) {
      return false;
    }
    final Object this$visualization = getVisualization();
    final Object other$visualization = other.getVisualization();
    if (this$visualization == null
        ? other$visualization != null
        : !this$visualization.equals(other$visualization)) {
      return false;
    }
    return true;
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
  private static List<DecisionFilterDto<?>> $default$filter() {
    return new ArrayList<>();
  }

  private static ProcessReportDistributedByDto<?> $default$distributedBy() {
    return new NoneDistributedByDto();
  }

  public static DecisionReportDataDtoBuilder<?, ?> builder() {
    return new DecisionReportDataDtoBuilderImpl();
  }

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

    private @Valid List<DecisionFilterDto<?>> filter$value;
    private boolean filter$set;
    private DecisionViewDto view;
    private DecisionGroupByDto<?> groupBy;
    private ProcessReportDistributedByDto<?> distributedBy$value;
    private boolean distributedBy$set;
    private DecisionVisualization visualization;

    public B filter(@Valid final List<DecisionFilterDto<?>> filter) {
      filter$value = filter;
      filter$set = true;
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
      distributedBy$value = distributedBy;
      distributedBy$set = true;
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
          + ", filter$value="
          + filter$value
          + ", view="
          + view
          + ", groupBy="
          + groupBy
          + ", distributedBy$value="
          + distributedBy$value
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
