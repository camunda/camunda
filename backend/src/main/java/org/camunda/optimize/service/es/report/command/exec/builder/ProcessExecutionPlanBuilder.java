/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec.builder;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

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

    private Class<? extends ProcessViewPart> viewPartClass;

    private AddGroupByBuilder(final Class<? extends ProcessViewPart> viewPartClass) {
      this.viewPartClass = viewPartClass;
    }

    public AddDistributedByBuilder groupBy(Class<? extends ProcessGroupByPart> groupByPartClass) {
      return new AddDistributedByBuilder(viewPartClass, groupByPartClass);
    }
  }

  public class AddDistributedByBuilder {

    private Class<? extends ProcessViewPart> viewPartClass;
    private Class<? extends ProcessGroupByPart> groupByPartClass;

    public AddDistributedByBuilder(final Class<? extends ProcessViewPart> viewPartClass,
                                   final Class<? extends ProcessGroupByPart> groupByPartClass) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
    }

    public ReportResultTypeBuilder distributedBy(Class<? extends ProcessDistributedByPart> distributedByPartClass) {
      return new ReportResultTypeBuilder(viewPartClass, groupByPartClass, distributedByPartClass);
    }
  }

  public class ReportResultTypeBuilder {

    private Class<? extends ProcessViewPart> viewPartClass;
    private Class<? extends ProcessGroupByPart> groupByPartClass;
    private Class<? extends ProcessDistributedByPart> distributedByPartClass;

    public ReportResultTypeBuilder(final Class<? extends ProcessViewPart> viewPartClass,
                                   final Class<? extends ProcessGroupByPart> groupByPartClass, final Class<?
      extends ProcessDistributedByPart> distributedByPartClass) {
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

  public class ExecuteBuildBuilder<R extends ProcessReportResultDto> {

    private Class<? extends ProcessViewPart> viewPartClass;
    private Class<? extends ProcessGroupByPart> groupByPartClass;
    private Class<? extends ProcessDistributedByPart> distributedByPartClass;
    private Function<CompositeCommandResult, R> mapToReportResult;

    public ExecuteBuildBuilder(final Class<? extends ProcessViewPart> viewPartClass,
                               final Class<? extends ProcessGroupByPart> groupByPartClass, final Class<?
      extends ProcessDistributedByPart> distributedByPartClass, final Function<CompositeCommandResult, R> mapToReportResult) {
      this.viewPartClass = viewPartClass;
      this.groupByPartClass = groupByPartClass;
      this.distributedByPartClass = distributedByPartClass;
      this.mapToReportResult = mapToReportResult;
    }

    public ProcessReportCmdExecutionPlan<R> build() {
      final ProcessViewPart viewPart = context.getBean(this.viewPartClass);
      final ProcessGroupByPart groupByPart = context.getBean(this.groupByPartClass);
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
