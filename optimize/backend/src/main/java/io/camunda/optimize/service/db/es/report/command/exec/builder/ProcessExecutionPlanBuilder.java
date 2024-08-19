/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.exec.builder;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.command.exec.ProcessReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.ProcessDistributedByPart;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.GroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewPart;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import java.util.List;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ProcessExecutionPlanBuilder {

  private final ApplicationContext context;
  private final DatabaseClient databaseClient;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  public ProcessExecutionPlanBuilder(
      final ApplicationContext context,
      final DatabaseClient databaseClient,
      final ProcessQueryFilterEnhancer processQueryFilterEnhancer,
      final ProcessDefinitionReader processDefinitionReader) {
    this.context = context;
    this.databaseClient = databaseClient;
    this.processQueryFilterEnhancer = processQueryFilterEnhancer;
    this.processDefinitionReader = processDefinitionReader;
  }

  AddViewPartBuilder createExecutionPlan() {
    return new AddViewPartBuilder();
  }

  public class AddViewPartBuilder {

    public AddGroupByBuilder view(final Class<? extends ProcessViewPart> viewPartClass) {
      return new AddGroupByBuilder(viewPartClass);
    }
  }

  public class AddGroupByBuilder {

    private final Class<? extends ProcessViewPart> viewPartClass;

    private AddGroupByBuilder(final Class<? extends ProcessViewPart> viewPartClass) {
      this.viewPartClass = viewPartClass;
    }

    public AddDistributedByBuilder groupBy(
        final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass) {
      return new AddDistributedByBuilder(viewPartClass, groupByPartClass);
    }
  }

  public class AddDistributedByBuilder {

    private final Class<? extends ProcessViewPart> viewPartClass;
    private final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass;

    public AddDistributedByBuilder(
        final Class<? extends ProcessViewPart> viewPartClass,
        final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
    }

    public ReportResultTypeBuilder distributedBy(
        final Class<? extends ProcessDistributedByPart> distributedByPartClass) {
      return new ReportResultTypeBuilder(viewPartClass, groupByPartClass, distributedByPartClass);
    }
  }

  public class ReportResultTypeBuilder {

    private final Class<? extends ProcessViewPart> viewPartClass;
    private final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass;
    private final Class<? extends ProcessDistributedByPart> distributedByPartClass;

    public ReportResultTypeBuilder(
        final Class<? extends ProcessViewPart> viewPartClass,
        final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass,
        final Class<? extends ProcessDistributedByPart> distributedByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
    }

    public <T extends RawDataInstanceDto> ExecuteBuildBuilder<List<T>> resultAsRawData() {
      return new ExecuteBuildBuilder<>(
          viewPartClass,
          groupByPartClass,
          distributedByPartClass,
          CompositeCommandResult::transformToRawData);
    }

    public ExecuteBuildBuilder<Double> resultAsNumber() {
      return new ExecuteBuildBuilder<>(
          viewPartClass,
          groupByPartClass,
          distributedByPartClass,
          CompositeCommandResult::transformToNumber);
    }

    public ExecuteBuildBuilder<List<MapResultEntryDto>> resultAsMap() {
      return new ExecuteBuildBuilder<>(
          viewPartClass,
          groupByPartClass,
          distributedByPartClass,
          CompositeCommandResult::transformToMap);
    }

    public ExecuteBuildBuilder<List<HyperMapResultEntryDto>> resultAsHyperMap() {
      return new ExecuteBuildBuilder<>(
          viewPartClass,
          groupByPartClass,
          distributedByPartClass,
          CompositeCommandResult::transformToHyperMap);
    }
  }

  public class ExecuteBuildBuilder<T> {

    private final Class<? extends ProcessViewPart> viewPartClass;
    private final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass;
    private final Class<? extends ProcessDistributedByPart> distributedByPartClass;
    private final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult;

    public ExecuteBuildBuilder(
        final Class<? extends ProcessViewPart> viewPartClass,
        final Class<? extends GroupByPart<ProcessReportDataDto>> groupByPartClass,
        final Class<? extends ProcessDistributedByPart> distributedByPartClass,
        final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
      this.mapToReportResult = mapToReportResult;
    }

    public ProcessReportCmdExecutionPlan<T> build() {
      final ProcessViewPart viewPart = context.getBean(viewPartClass);
      final GroupByPart<ProcessReportDataDto> groupByPart = context.getBean(groupByPartClass);
      final ProcessDistributedByPart distributedByPart = context.getBean(distributedByPartClass);
      return new ProcessReportCmdExecutionPlan<>(
          viewPart,
          groupByPart,
          distributedByPart,
          mapToReportResult,
          databaseClient,
          processDefinitionReader,
          processQueryFilterEnhancer);
    }
  }
}
