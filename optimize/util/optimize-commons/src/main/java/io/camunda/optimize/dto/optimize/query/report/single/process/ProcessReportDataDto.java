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
@ProcessFiltersMustReferenceExistingDefinitionsConstraint
public class ProcessReportDataDto extends SingleReportDataDto implements Combinable {

  private static final String COMMAND_KEY_SEPARATOR = "_";
  private static final String MISSING_COMMAND_PART_PLACEHOLDER = "null";

  @Builder.Default @Valid protected List<ProcessFilterDto<?>> filter = new ArrayList<>();
  protected ProcessViewDto view;
  protected ProcessGroupByDto<?> groupBy;

  @Builder.Default
  protected ProcessReportDistributedByDto<?> distributedBy = new ProcessReportDistributedByDto<>();

  protected ProcessVisualization visualization;
  @Builder.Default protected boolean managementReport = false;
  @Builder.Default protected boolean instantPreviewReport = false;

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

  public static final class Fields {

    public static final String filter = "filter";
    public static final String view = "view";
    public static final String groupBy = "groupBy";
    public static final String distributedBy = "distributedBy";
    public static final String visualization = "visualization";
    public static final String managementReport = "managementReport";
    public static final String instantPreviewReport = "instantPreviewReport";
  }
}
