/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec.builder;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewPart;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@RequiredArgsConstructor
@Component
public class DecisionExecutionPlanBuilder {

  private final ApplicationContext context;
  private final OptimizeElasticsearchClient esClient;
  private final DecisionQueryFilterEnhancer decisionQueryFilterEnhancer;
  private final DecisionDefinitionReader decisionDefinitionReader;

  AddViewPartBuilder createExecutionPlan() {
    return new AddViewPartBuilder();
  }

  public class AddViewPartBuilder {

    public AddGroupByBuilder view(Class<? extends DecisionViewPart> viewPartClass) {
      return new AddGroupByBuilder(viewPartClass);
    }
  }

  public class AddGroupByBuilder {

    private Class<? extends DecisionViewPart> viewPartClass;

    private AddGroupByBuilder(final Class<? extends DecisionViewPart> viewPartClass) {
      this.viewPartClass = viewPartClass;
    }

    public AddDistributedByBuilder groupBy(Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass) {
      return new AddDistributedByBuilder(viewPartClass, groupByPartClass);
    }
  }

  public class AddDistributedByBuilder {

    private Class<? extends DecisionViewPart> viewPartClass;
    private Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass;

    public AddDistributedByBuilder(final Class<? extends DecisionViewPart> viewPartClass,
                                   final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
    }

    public ReportResultTypeBuilder distributedBy(Class<? extends DecisionDistributedByPart> distributedByPartClass) {
      return new ReportResultTypeBuilder(viewPartClass, groupByPartClass, distributedByPartClass);
    }
  }

  public class ReportResultTypeBuilder {

    private Class<? extends DecisionViewPart> viewPartClass;
    private Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass;
    private Class<? extends DecisionDistributedByPart> distributedByPartClass;

    public ReportResultTypeBuilder(final Class<? extends DecisionViewPart> viewPartClass,
                                   final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass,
                                   final Class<? extends DecisionDistributedByPart> distributedByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
    }

    public ExecuteBuildBuilder<RawDataDecisionReportResultDto> resultAsRawData() {
      return new ExecuteBuildBuilder<>(
        viewPartClass,
        groupByPartClass,
        distributedByPartClass,
        CompositeCommandResult::transformToDecisionRawData
      );
    }

    public ExecuteBuildBuilder<NumberResultDto> resultAsNumber() {
      return new ExecuteBuildBuilder<>(
        viewPartClass,
        groupByPartClass,
        distributedByPartClass,
        CompositeCommandResult::transformToNumber
      );
    }

    public ExecuteBuildBuilder<ReportMapResultDto> resultAsMap() {
      return new ExecuteBuildBuilder<>(
        viewPartClass,
        groupByPartClass,
        distributedByPartClass,
        CompositeCommandResult::transformToMap
      );
    }
  }

  public class ExecuteBuildBuilder<R extends DecisionReportResultDto> {

    private Class<? extends DecisionViewPart> viewPartClass;
    private Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass;
    private Class<? extends DecisionDistributedByPart> distributedByPartClass;
    private Function<CompositeCommandResult, R> mapToReportResult;

    public ExecuteBuildBuilder(final Class<? extends DecisionViewPart> viewPartClass,
                               final Class<? extends GroupByPart<DecisionReportDataDto>> groupByPartClass,
                               final Class<? extends DecisionDistributedByPart> distributedByPartClass,
                               final Function<CompositeCommandResult, R> mapToReportResult) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
      this.mapToReportResult = mapToReportResult;
    }

    public DecisionReportCmdExecutionPlan<R> build() {
      final DecisionViewPart viewPart = context.getBean(this.viewPartClass);
      final GroupByPart<DecisionReportDataDto> groupByPart = context.getBean(this.groupByPartClass);
      final DecisionDistributedByPart distributedByPart = context.getBean(this.distributedByPartClass);
      return new DecisionReportCmdExecutionPlan<>(
        viewPart,
        groupByPart,
        distributedByPart,
        mapToReportResult,
        esClient,
        decisionDefinitionReader,
        decisionQueryFilterEnhancer
      );
    }
  }

}
