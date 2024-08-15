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
    if (b.managementReport$set) {
      managementReport = b.managementReport$value;
    } else {
      managementReport = $default$managementReport();
    }
    if (b.instantPreviewReport$set) {
      instantPreviewReport = b.instantPreviewReport$value;
    } else {
      instantPreviewReport = $default$instantPreviewReport();
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
    result = result * PRIME + (isManagementReport() ? 79 : 97);
    result = result * PRIME + (isInstantPreviewReport() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessReportDataDto)) {
      return false;
    }
    final ProcessReportDataDto other = (ProcessReportDataDto) o;
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
    if (isManagementReport() != other.isManagementReport()) {
      return false;
    }
    if (isInstantPreviewReport() != other.isInstantPreviewReport()) {
      return false;
    }
    return true;
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
  private static List<ProcessFilterDto<?>> $default$filter() {
    return new ArrayList<>();
  }

  private static ProcessReportDistributedByDto<?> $default$distributedBy() {
    return new ProcessReportDistributedByDto<>();
  }

  private static boolean $default$managementReport() {
    return false;
  }

  private static boolean $default$instantPreviewReport() {
    return false;
  }

  public static ProcessReportDataDtoBuilder<?, ?> builder() {
    return new ProcessReportDataDtoBuilderImpl();
  }

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

    private @Valid List<ProcessFilterDto<?>> filter$value;
    private boolean filter$set;
    private ProcessViewDto view;
    private ProcessGroupByDto<?> groupBy;
    private ProcessReportDistributedByDto<?> distributedBy$value;
    private boolean distributedBy$set;
    private ProcessVisualization visualization;
    private boolean managementReport$value;
    private boolean managementReport$set;
    private boolean instantPreviewReport$value;
    private boolean instantPreviewReport$set;

    public B filter(@Valid final List<ProcessFilterDto<?>> filter) {
      filter$value = filter;
      filter$set = true;
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
      distributedBy$value = distributedBy;
      distributedBy$set = true;
      return self();
    }

    public B visualization(final ProcessVisualization visualization) {
      this.visualization = visualization;
      return self();
    }

    public B managementReport(final boolean managementReport) {
      managementReport$value = managementReport;
      managementReport$set = true;
      return self();
    }

    public B instantPreviewReport(final boolean instantPreviewReport) {
      instantPreviewReport$value = instantPreviewReport;
      instantPreviewReport$set = true;
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
          + ", managementReport$value="
          + managementReport$value
          + ", instantPreviewReport$value="
          + instantPreviewReport$value
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
