/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec.builder;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@RequiredArgsConstructor
@Component
public class ReportCmdExecutionPlanBuilder {

  private final ApplicationContext context;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  public AddViewPartBuilder createExecutionPlan() {
    return new AddViewPartBuilder();
  }

  public class AddViewPartBuilder {

    public AddGroupByBuilder view(Class<? extends ViewPart> viewPartClass) {
      return new AddGroupByBuilder(viewPartClass);
    }
  }

  public class AddGroupByBuilder {

    private Class<? extends ViewPart> viewPartClass;

    private AddGroupByBuilder(final Class<? extends ViewPart> viewPartClass) {
      this.viewPartClass = viewPartClass;
    }

    public AddDistributedByBuilder groupBy(Class<? extends GroupByPart> groupByPartClass) {
      return new AddDistributedByBuilder(viewPartClass, groupByPartClass);
    }
  }

  public class AddDistributedByBuilder {

    private Class<? extends ViewPart> viewPartClass;
    private Class<? extends GroupByPart> groupByPartClass;

    public AddDistributedByBuilder(final Class<? extends ViewPart> viewPartClass,
                                   final Class<? extends GroupByPart> groupByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
    }

    public ReportResultTypeBuilder distributedBy(Class<? extends DistributedByPart> distributedByPartClass) {
      return new ReportResultTypeBuilder(viewPartClass, groupByPartClass, distributedByPartClass);
    }
  }

  public class ReportResultTypeBuilder {

    private Class<? extends ViewPart> viewPartClass;
    private Class<? extends GroupByPart> groupByPartClass;
    private Class<? extends DistributedByPart> distributedByPartClass;

    public ReportResultTypeBuilder(final Class<? extends ViewPart> viewPartClass,
                                   final Class<? extends GroupByPart> groupByPartClass, final Class<?
      extends DistributedByPart> distributedByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
    }

    public ExecuteBuildBuilder<NumberResultDto> resultAsNumber() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToNumber
      );
    }

    public ExecuteBuildBuilder<ReportMapResultDto> resultAsMap() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToMap
      );
    }

    public ExecuteBuildBuilder<ReportHyperMapResultDto> resultAsHyperMap() {
      return new ExecuteBuildBuilder<>(
        viewPartClass, groupByPartClass, distributedByPartClass, CompositeCommandResult::transformToHyperMap
      );
    }
  }

  public class ExecuteBuildBuilder<R extends SingleReportResultDto> {

    private Class<? extends ViewPart> viewPartClass;
    private Class<? extends GroupByPart> groupByPartClass;
    private Class<? extends DistributedByPart> distributedByPartClass;
    private Function<CompositeCommandResult, R> mapToReportResult;

    public ExecuteBuildBuilder(final Class<? extends ViewPart> viewPartClass,
                               final Class<? extends GroupByPart> groupByPartClass, final Class<?
      extends DistributedByPart> distributedByPartClass, final Function<CompositeCommandResult, R> mapToReportResult) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
      this.mapToReportResult = mapToReportResult;
    }

    public ReportCmdExecutionPlan<R> build() {
      final ViewPart viewPart = context.getBean(this.viewPartClass);
      final GroupByPart groupByPart = context.getBean(this.groupByPartClass);
      final DistributedByPart distributedByPart = context.getBean(this.distributedByPartClass);
      return new ReportCmdExecutionPlan<>(
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
