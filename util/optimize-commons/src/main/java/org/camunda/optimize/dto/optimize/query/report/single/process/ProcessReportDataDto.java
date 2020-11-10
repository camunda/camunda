/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@SuperBuilder
@FieldNameConstants
public class ProcessReportDataDto extends SingleReportDataDto implements Combinable {

  protected String processDefinitionKey;
  @Builder.Default
  protected List<String> processDefinitionVersions = new ArrayList<>();
  protected String processDefinitionName;
  @Builder.Default
  protected List<String> tenantIds = Collections.singletonList(null);
  @Builder.Default
  protected List<ProcessFilterDto<?>> filter = new ArrayList<>();
  protected ProcessViewDto view;
  protected ProcessGroupByDto<?> groupBy;
  protected ProcessDistributedByDto<?> distributedBy = new ProcessDistributedByDto<>();
  protected ProcessVisualization visualization;

  @JsonIgnore
  @Override
  public String getDefinitionKey() {
    return processDefinitionKey;
  }

  @JsonIgnore
  @Override
  public List<String> getDefinitionVersions() {
    return processDefinitionVersions;
  }

  @JsonIgnore
  @Override
  public String getDefinitionName() {
    return processDefinitionName;
  }

  @JsonIgnore
  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
  }

  @JsonIgnore
  public boolean isFrequencyReport() {
    return Optional.ofNullable(view)
      .map(ProcessViewDto::getProperty)
      .map(p -> p.equals(ProcessViewProperty.FREQUENCY))
      .orElse(false);
  }

  @JsonIgnore
  @Override
  public String createCommandKey() {
    String viewCommandKey = view == null ? "null" : view.createCommandKey();
    String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    String distributedByCommandKey = createDistributedByCommandKey();
    String configurationCommandKey = Optional.ofNullable(getConfiguration())
      .map(SingleReportConfigurationDto::createCommandKey)
      .orElse("null");
    return viewCommandKey + "_" +
      groupByCommandKey + "_" +
      Stream.of(distributedByCommandKey, configurationCommandKey)
        .filter(Objects::nonNull)
        .collect(Collectors.joining("-"));
  }

  public String createDistributedByCommandKey() {
    if (distributedBy != null
      && (isModelElementCommand() || isInstanceCommand())) {
      return distributedBy.createCommandKey();
    }
    return null;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessReportDataDto)) {
      return false;
    }
    ProcessReportDataDto that = (ProcessReportDataDto) o;
    return Combinable.isCombinable(view, that.view) &&
      isGroupByCombinable(that) &&
      Combinable.isCombinable(getDistributedBy(), that.getDistributedBy()) &&
      Objects.equals(visualization, that.visualization);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  @JsonIgnore
  public List<ProcessFilterDto<?>> getAdditionalFiltersForReportType() {
    if (isGroupByEndDateReport()) {
      return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
    }
    return Collections.emptyList();
  }

  private boolean isGroupByCombinable(final ProcessReportDataDto that) {
    if (Combinable.isCombinable(this.groupBy, that.groupBy)) {
      if (isGroupByDateVariableReport()) {
        return this.getConfiguration()
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
    return this.getConfiguration().getCustomBucket().isActive()
      && that.getConfiguration().getCustomBucket().isActive()
      && Objects.equals(
      this.getConfiguration().getCustomBucket().getBucketSize(),
      that.getConfiguration().getCustomBucket().getBucketSize()
    ) || isBucketSizeIrrelevant(this) && isBucketSizeIrrelevant(that);
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
      &&
      (
        ProcessGroupByType.VARIABLE.equals(groupBy.getType())
          && (VariableType.getNumericTypes().contains(((VariableGroupByDto) groupBy).getValue().getType()))
          || ProcessGroupByType.DURATION.equals(groupBy.getType())
      );
  }

  private boolean isModelElementCommand() {
    return nonNull(view) && nonNull(view.getEntity()) &&
      (ProcessViewEntity.USER_TASK.equals(view.getEntity())
        || ProcessViewEntity.FLOW_NODE.equals(view.getEntity()));
  }

  private boolean isInstanceCommand() {
    return nonNull(view)
      && nonNull(view.getEntity())
      && ProcessViewEntity.PROCESS_INSTANCE.equals(view.getEntity());
  }

}
