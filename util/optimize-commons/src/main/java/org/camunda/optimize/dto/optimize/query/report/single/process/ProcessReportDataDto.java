/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessParametersDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
public class ProcessReportDataDto extends SingleReportDataDto implements Combinable {

  protected String processDefinitionKey;
  protected List<String> processDefinitionVersions = new ArrayList<>();
  protected String processDefinitionName;
  protected List<String> tenantIds = Collections.singletonList(null);
  protected List<ProcessFilterDto> filter = new ArrayList<>();
  protected ProcessViewDto view;
  protected ProcessGroupByDto groupBy;
  protected ProcessVisualization visualization;
  protected ProcessParametersDto parameters = new ProcessParametersDto();

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
  @Override
  public String createCommandKey() {
    String viewCommandKey = view == null ? "null" : view.createCommandKey();
    String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    String processPartCommandKey = Optional.ofNullable(getParameters())
      .flatMap(parameters -> Optional.ofNullable(parameters.getProcessPart()))
      .map(ProcessPartDto::createCommandKey)
      .orElse("null");
    String configurationCommandKey = Optional.ofNullable(getConfiguration())
      .map(c -> c.createCommandKey(getView(), getGroupBy()))
      .orElse("null");
    return viewCommandKey + "_" + groupByCommandKey + "_" + processPartCommandKey + "_" + configurationCommandKey;
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
      Combinable.isCombinable(groupBy, that.groupBy) &&
      Combinable.isCombinable(getConfiguration(), that.getConfiguration()) &&
      Objects.equals(visualization, that.visualization);
  }

}
