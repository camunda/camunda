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
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.command.exec.DecisionReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.decision.DecisionDistributedByPart;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.GroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.decision.DecisionViewPart;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import java.util.List;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DecisionExecutionPlanBuilder {

  private final ApplicationContext context;
  private final DatabaseClient databaseClient;
  private final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  private final DecisionDefinitionReader decisionDefinitionReader;

  public DecisionExecutionPlanBuilder(
      final ApplicationContext context,
      final DatabaseClient databaseClient,
      final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer,
      final DecisionDefinitionReader decisionDefinitionReader) {
    this.context = context;
    this.databaseClient = databaseClient;
    this.decisionQueryFilterEnhancer = decisionQueryFilterEnhancer;
    this.decisionDefinitionReader = decisionDefinitionReader;
  }

  AddViewPartBuilder createExecutionPlan() {
    return new AddViewPartBuilder();
  }

  public class AddViewPartBuilder {

    public AddGroupByBuilder view(final Class<? extends DecisionViewPart> viewPartClass) {
      return new AddGroupByBuilder(viewPartClass);
    }
  }

  public class AddGroupByBuilder {

    private final Class<? extends DecisionViewPart> viewPartClass;

    private AddGroupByBuilder(final Class<? extends DecisionViewPart> viewPartClass) {
      this.viewPartClass = viewPartClass;
    }

    public AddDistributedByBuilder groupBy(
        final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass) {
      return new AddDistributedByBuilder(viewPartClass, groupByPartClass);
    }
  }

  public class AddDistributedByBuilder {

    private final Class<? extends DecisionViewPart> viewPartClass;
    private final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass;

    public AddDistributedByBuilder(
        final Class<? extends DecisionViewPart> viewPartClass,
        final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
    }

    public ReportResultTypeBuilder distributedBy(
        final Class<? extends DecisionDistributedByPart> distributedByPartClass) {
      return new ReportResultTypeBuilder(viewPartClass, groupByPartClass, distributedByPartClass);
    }
  }

  public class ReportResultTypeBuilder {

    private final Class<? extends DecisionViewPart> viewPartClass;
    private final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass;
    private final Class<? extends DecisionDistributedByPart> distributedByPartClass;

    public ReportResultTypeBuilder(
        final Class<? extends DecisionViewPart> viewPartClass,
        final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass,
        final Class<? extends DecisionDistributedByPart> distributedByPartClass) {
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
  }

  public class ExecuteBuildBuilder<T> {

    private final Class<? extends DecisionViewPart> viewPartClass;
    private final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass;
    private final Class<? extends DecisionDistributedByPart> distributedByPartClass;
    private final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult;

    public ExecuteBuildBuilder(
        final Class<? extends DecisionViewPart> viewPartClass,
        final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass,
        final Class<? extends DecisionDistributedByPart> distributedByPartClass,
        final Function<CompositeCommandResult, CommandEvaluationResult<T>> mapToReportResult) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
      this.mapToReportResult = mapToReportResult;
    }

    public DecisionReportCmdExecutionPlan<T> build() {
      final DecisionViewPart viewPart = context.getBean(viewPartClass);
      final GroupByPart<DecisionReportDataDto> groupByPart = context.getBean(groupByPartClass);
      final DecisionDistributedByPart distributedByPart = context.getBean(distributedByPartClass);
      return new DecisionReportCmdExecutionPlan<>(
          viewPart,
          groupByPart,
          distributedByPart,
          mapToReportResult,
          databaseClient,
          decisionDefinitionReader,
          decisionQueryFilterEnhancer);
    }
  }
}
