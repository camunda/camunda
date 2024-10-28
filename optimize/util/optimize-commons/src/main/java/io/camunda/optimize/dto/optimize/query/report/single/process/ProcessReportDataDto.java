/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process;

import static java.util.Objects.nonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.validation.ProcessFiltersMustReferenceExistingDefinitionsConstraint;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@ProcessFiltersMustReferenceExistingDefinitionsConstraint
public class ProcessReportDataDto extends SingleReportDataDto implements Combinable {

  private static final String COMMAND_KEY_SEPARATOR = "_";
  private static final String MISSING_COMMAND_PART_PLACEHOLDER = "null";

  @Valid protected List<ProcessFilterDto<?>> filter = new ArrayList<>();
  protected ProcessViewDto view;
  protected ProcessGroupByDto<?> groupBy;

  protected ProcessReportDistributedByDto<?> distributedBy = new ProcessReportDistributedByDto<>();

  protected ProcessVisualization visualization;
  protected boolean managementReport = false;
  protected boolean instantPreviewReport = false;

  public ProcessReportDataDto(
      @Valid final List<ProcessFilterDto<?>> filter,
      final ProcessViewDto view,
      final ProcessGroupByDto<?> groupBy,
      final ProcessReportDistributedByDto<?> distributedBy,
      final ProcessVisualization visualization,
      final boolean managementReport,
      final boolean instantPreviewReport) {
    this.filter = filter;
    this.view = view;
    this.groupBy = groupBy;
    this.distributedBy = distributedBy;
    this.visualization = visualization;
    this.managementReport = managementReport;
    this.instantPreviewReport = instantPreviewReport;
  }

  public ProcessReportDataDto() {}

  protected ProcessReportDataDto(final ProcessReportDataDtoBuilder<?, ?> b) {
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
    if (b.managementReportSet) {
      managementReport = b.managementReportValue;
    } else {
      managementReport = defaultManagementReport();
    }
    if (b.instantPreviewReportSet) {
      instantPreviewReport = b.instantPreviewReportValue;
    } else {
      instantPreviewReport = defaultInstantPreviewReport();
    }
  }

  public String getProcessDefinitionKey() {
    return getDefinitionKey();
  }

  @JsonIgnore
  public void setProcessDefinitionKey(final String key) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setKey(key);
  }

  @JsonIgnore
  public void setProcessDefinitionName(final String name) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setName(name);
  }

  public List<String> getProcessDefinitionVersions() {
    return getDefinitionVersions();
  }

  @JsonIgnore
  public void setProcessDefinitionVersions(final List<String> versions) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setVersions(versions);
  }

  @JsonIgnore
  public void setProcessDefinitionVersion(final String version) {
    final List<ReportDataDefinitionDto> definitions = getDefinitions();
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setVersion(version);
  }

  @Override
  public List<ViewProperty> getViewProperties() {
    return view.getProperties();
  }

  @JsonIgnore
  @Override
  public String createCommandKey() {
    return createCommandKeys().get(0);
  }

  @Override
  public List<String> createCommandKeys() {
    final String groupByCommandKey =
        groupBy == null ? MISSING_COMMAND_PART_PLACEHOLDER : groupBy.createCommandKey();
    final String distributedByCommandKey = createDistributedByCommandKey();
    final String configurationCommandKey =
        Optional.ofNullable(getConfiguration())
            .map(SingleReportConfigurationDto::createCommandKey)
            .orElse(MISSING_COMMAND_PART_PLACEHOLDER);
    return Optional.ofNullable(view)
        .map(ProcessViewDto::createCommandKeys)
        .orElse(Collections.singletonList(MISSING_COMMAND_PART_PLACEHOLDER))
        .stream()
        .map(
            viewKey ->
                String.join(
                    COMMAND_KEY_SEPARATOR,
                    viewKey,
                    groupByCommandKey,
                    distributedByCommandKey,
                    configurationCommandKey))
        .collect(Collectors.toList());
  }

  public String createDistributedByCommandKey() {
    if (distributedBy != null && (isModelElementCommand() || isInstanceCommand())) {
      return distributedBy.createCommandKey();
    }
    return null;
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessReportDataDto)) {
      return false;
    }
    final ProcessReportDataDto that = (ProcessReportDataDto) o;
    return Combinable.isCombinable(getView(), that.getView())
        && isGroupByCombinable(that)
        && Combinable.isCombinable(getDistributedBy(), that.getDistributedBy())
        && Objects.equals(getVisualization(), that.getVisualization())
        && getConfiguration().isCombinable(that.getConfiguration());
  }

  @JsonIgnore
  public List<ProcessFilterDto<?>> getAdditionalFiltersForReportType() {
    if (isGroupByEndDateReport()) {
      return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
    } else if (isUserTaskReport()) {
      return ProcessFilterBuilder.filter().userTaskFlowNodesOnly().add().buildList();
    }
    return Collections.emptyList();
  }

  public boolean isUserTaskReport() {
    return nonNull(view) && ProcessViewEntity.USER_TASK.equals(view.getEntity());
  }

  @JsonIgnore
  public Map<String, List<ProcessFilterDto<?>>> groupFiltersByDefinitionIdentifier() {
    final Map<String, List<ProcessFilterDto<?>>> filterByDefinition = new HashMap<>();
    getFilter()
        .forEach(
            filterDto ->
                filterDto
                    .getAppliedTo()
                    .forEach(
                        definitionIdentifier ->
                            filterByDefinition
                                .computeIfAbsent(definitionIdentifier, key -> new ArrayList<>())
                                .add(filterDto)));
    return filterByDefinition;
  }

  private boolean isGroupByCombinable(final ProcessReportDataDto that) {
    if (Combinable.isCombinable(groupBy, that.groupBy)) {
      if (isGroupByDateVariableReport()) {
        return getConfiguration()
            .getGroupByDateVariableUnit()
            .equals(that.getConfiguration().getGroupByDateVariableUnit());
      } else if (isGroupByNumberReport()) {
        return isBucketSizeCombinable(that);
      }
      return true;
    }
    return false;
  }

  private boolean isBucketSizeCombinable(final ProcessReportDataDto that) {
    return getConfiguration().getCustomBucket().isActive()
            && that.getConfiguration().getCustomBucket().isActive()
            && Objects.equals(
                getConfiguration().getCustomBucket().getBucketSize(),
                that.getConfiguration().getCustomBucket().getBucketSize())
        || isBucketSizeIrrelevant(this) && isBucketSizeIrrelevant(that);
  }

  private boolean isBucketSizeIrrelevant(final ProcessReportDataDto reportData) {
    // Bucket size settings for combined reports are not relevant if custom bucket config is
    // inactive or bucket size is null
    if (reportData.getConfiguration().getCustomBucket().isActive()) {
      return reportData.getConfiguration().getCustomBucket().getBucketSize() == null;
    }
    return true;
  }

  private boolean isGroupByDateVariableReport() {
    return groupBy != null
        && ProcessGroupByType.VARIABLE.equals(groupBy.getType())
        && VariableType.DATE.equals(((VariableGroupByDto) groupBy).getValue().getType());
  }

  private boolean isGroupByEndDateReport() {
    return groupBy != null
        && ProcessViewEntity.PROCESS_INSTANCE.equals(view.getEntity())
        && ProcessGroupByType.END_DATE.equals(groupBy.getType());
  }

  private boolean isGroupByNumberReport() {
    return groupBy != null
        && (ProcessGroupByType.VARIABLE.equals(groupBy.getType())
                && (VariableType.getNumericTypes()
                    .contains(((VariableGroupByDto) groupBy).getValue().getType()))
            || ProcessGroupByType.DURATION.equals(groupBy.getType()));
  }

  private boolean isModelElementCommand() {
    return nonNull(view)
        && nonNull(view.getEntity())
        && (ProcessViewEntity.USER_TASK.equals(view.getEntity())
            || ProcessViewEntity.FLOW_NODE.equals(view.getEntity()));
  }

  private boolean isInstanceCommand() {
    return nonNull(view)
        && nonNull(view.getEntity())
        && ProcessViewEntity.PROCESS_INSTANCE.equals(view.getEntity());
  }

  public @Valid List<ProcessFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(@Valid final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  public ProcessViewDto getView() {
    return view;
  }

  public void setView(final ProcessViewDto view) {
    this.view = view;
  }

  public ProcessGroupByDto<?> getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(final ProcessGroupByDto<?> groupBy) {
    this.groupBy = groupBy;
  }

  public ProcessReportDistributedByDto<?> getDistributedBy() {
    return distributedBy;
  }

  public void setDistributedBy(final ProcessReportDistributedByDto<?> distributedBy) {
    this.distributedBy = distributedBy;
  }

  public ProcessVisualization getVisualization() {
    return visualization;
  }

  public void setVisualization(final ProcessVisualization visualization) {
    this.visualization = visualization;
  }

  public boolean isManagementReport() {
    return managementReport;
  }

  public void setManagementReport(final boolean managementReport) {
    this.managementReport = managementReport;
  }

  public boolean isInstantPreviewReport() {
    return instantPreviewReport;
  }

  public void setInstantPreviewReport(final boolean instantPreviewReport) {
    this.instantPreviewReport = instantPreviewReport;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessReportDataDto;
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
    return "ProcessReportDataDto(filter="
        + getFilter()
        + ", view="
        + getView()
        + ", groupBy="
        + getGroupBy()
        + ", distributedBy="
        + getDistributedBy()
        + ", visualization="
        + getVisualization()
        + ", managementReport="
        + isManagementReport()
        + ", instantPreviewReport="
        + isInstantPreviewReport()
        + ")";
  }

  @Valid
  private static List<ProcessFilterDto<?>> defaultFilter() {
    return new ArrayList<>();
  }

  private static ProcessReportDistributedByDto<?> defaultDistributedBy() {
    return new ProcessReportDistributedByDto<>();
  }

  private static boolean defaultManagementReport() {
    return false;
  }

  private static boolean defaultInstantPreviewReport() {
    return false;
  }

  public static ProcessReportDataDtoBuilder<?, ?> builder() {
    return new ProcessReportDataDtoBuilderImpl();
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String filter = "filter";
    public static final String view = "view";
    public static final String groupBy = "groupBy";
    public static final String distributedBy = "distributedBy";
    public static final String visualization = "visualization";
    public static final String managementReport = "managementReport";
    public static final String instantPreviewReport = "instantPreviewReport";
  }

  public abstract static class ProcessReportDataDtoBuilder<
          C extends ProcessReportDataDto, B extends ProcessReportDataDtoBuilder<C, B>>
      extends SingleReportDataDtoBuilder<C, B> {

    private @Valid List<ProcessFilterDto<?>> filterValue;
    private boolean filterSet;
    private ProcessViewDto view;
    private ProcessGroupByDto<?> groupBy;
    private ProcessReportDistributedByDto<?> distributedByValue;
    private boolean distributedBySet;
    private ProcessVisualization visualization;
    private boolean managementReportValue;
    private boolean managementReportSet;
    private boolean instantPreviewReportValue;
    private boolean instantPreviewReportSet;

    public B filter(@Valid final List<ProcessFilterDto<?>> filter) {
      filterValue = filter;
      filterSet = true;
      return self();
    }

    public B view(final ProcessViewDto view) {
      this.view = view;
      return self();
    }

    public B groupBy(final ProcessGroupByDto<?> groupBy) {
      this.groupBy = groupBy;
      return self();
    }

    public B distributedBy(final ProcessReportDistributedByDto<?> distributedBy) {
      distributedByValue = distributedBy;
      distributedBySet = true;
      return self();
    }

    public B visualization(final ProcessVisualization visualization) {
      this.visualization = visualization;
      return self();
    }

    public B managementReport(final boolean managementReport) {
      managementReportValue = managementReport;
      managementReportSet = true;
      return self();
    }

    public B instantPreviewReport(final boolean instantPreviewReport) {
      instantPreviewReportValue = instantPreviewReport;
      instantPreviewReportSet = true;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "ProcessReportDataDto.ProcessReportDataDtoBuilder(super="
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
          + ", managementReportValue="
          + managementReportValue
          + ", instantPreviewReportValue="
          + instantPreviewReportValue
          + ")";
    }
  }

  private static final class ProcessReportDataDtoBuilderImpl
      extends ProcessReportDataDtoBuilder<ProcessReportDataDto, ProcessReportDataDtoBuilderImpl> {

    private ProcessReportDataDtoBuilderImpl() {}

    @Override
    protected ProcessReportDataDtoBuilderImpl self() {
      return this;
    }

    @Override
    public ProcessReportDataDto build() {
      return new ProcessReportDataDto(this);
    }
  }
}
