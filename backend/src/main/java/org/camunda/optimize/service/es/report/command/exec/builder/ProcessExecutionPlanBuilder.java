/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.exec.builder;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@RequiredArgsConstructor
@Component
public class ProcessExecutionPlanBuilder {

  private final ApplicationContext context;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  AddViewPartBuilder createExecutionPlan() {
    return new AddViewPartBuilder();
  }

  public class AddViewPartBuilder {

    public AddGroupByBuilder view(Class<? extends ProcessViewPart> viewPartClass) {
      return new AddGroupByBuilder(viewPartClass);
    }
  }

  public class AddGroupByBuilder {

    private final Class<? extends ProcessViewPart> viewPartClass;

    private AddGroupByBuilder(final Class<? extends ProcessViewPart> viewPartClass) {
      this.viewPartClass = viewPartClass;
    }

    public AddDistributedByBuilder groupBy(Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass) {
      return new AddDistributedByBuilder(viewPartClass, groupByPartClass);
    }
  }

  public class AddDistributedByBuilder {

    private final Class<? extends ProcessViewPart> viewPartClass;
    private final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass;

    public AddDistributedByBuilder(final Class<? extends ProcessViewPart> viewPartClass,
                                   final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
    }

    public ReportResultTypeBuilder distributedBy(Class<? extends ProcessDistributedByPart> distributedByPartClass) {
      return new ReportResultTypeBuilder(viewPartClass, groupByPartClass, distributedByPartClass);
    }
  }

  public class ReportResultTypeBuilder {

    private final Class<? extends ProcessViewPart> viewPartClass;
    private final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass;
    private final Class<? extends ProcessDistributedByPart> distributedByPartClass;

    public ReportResultTypeBuilder(final Class<? extends ProcessViewPart> viewPartClass,
                                   final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass,
                                   final Class<?
      extends ProcessDistributedByPart> distributedByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
    }

    public <T extends RawDataInstanceDto> ExecuteBuildBuilder<List<T>> resultAsRawData() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToRawData
      );
    }

    public ExecuteBuildBuilder<Double> resultAsNumber() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToNumber
      );
    }

    public ExecuteBuildBuilder<List<MapResultEntryDto>> resultAsMap() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToMap
      );
    }

    public ExecuteBuildBuilder<List<HyperMapResultEntryDto>> resultAsHyperMap() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToHyperMap
      );
    }
  }

  public class ExecuteBuildBuilder<T> {

    private final Class<? extends ProcessViewPart> viewPartClass;
    private final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass;
    private final Class<? extends ProcessDistributedByPart> distributedByPartClass;
    private final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult;

    public ExecuteBuildBuilder(final Class<? extends ProcessViewPart> viewPartClass,
                               final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass,
                               final Class<? extends ProcessDistributedByPart> distributedByPartClass,
                               final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
      this.mapToReportResult = mapToReportResult;
    }

    public ProcessReportCmdExecutionPlan<T> build() {
      final ProcessViewPart viewPart = context.getBean(this.viewPartClass);
      final GroupByPart<ProcessReportDataDto> groupByPart = context.getBean(this.groupByPartClass);
      final ProcessDistributedByPart distributedByPart = context.getBean(this.distributedByPartClass);
      return new ProcessReportCmdExecutionPlan<>(
        viewPart,
        groupByPart,
        distributedByPart,
        mapToReportResult,
        esClient,
        processDefinitionReader,
        processQueryFilterEnhancer
      );
    }
  }

}
