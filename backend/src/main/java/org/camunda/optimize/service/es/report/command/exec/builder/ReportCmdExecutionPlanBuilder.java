/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec.builder;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ReportCmdExecutionPlanBuilder {

  private final ApplicationContext context;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancer processQueryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  public AddResultTypeBuilder createExecutionPlan() {
    return new AddResultTypeBuilder();
  }

  public class AddResultTypeBuilder {
    public <R extends SingleReportResultDto> AddModulesBuilder<R> groupBy(Class<? extends GroupByPart<R>> groupByPart) {
      return new AddModulesBuilder<>(groupByPart);
    }
  }

  public class AddModulesBuilder<R extends SingleReportResultDto> {

    private Class<? extends GroupByPart<R>> groupByPartClass;

    private AddModulesBuilder(final Class<? extends GroupByPart<R>> groupByPartClass) {
      this.groupByPartClass = groupByPartClass;
    }

    public ExecuteBuildBuilder<R> addViewPart(Class<? extends ViewPart> viewPartClass) {
      return new ExecuteBuildBuilder<>(groupByPartClass, viewPartClass);
    }
  }

  public class ExecuteBuildBuilder<R extends SingleReportResultDto> {

    private Class<? extends GroupByPart<R>> groupByPartClass;
    private Class<? extends ViewPart> viewPartClass;

    private ExecuteBuildBuilder(final Class<? extends GroupByPart<R>> groupByPartClass,
                        final Class<? extends ViewPart> viewPartClass) {
      this.groupByPartClass = groupByPartClass;
      this.viewPartClass = viewPartClass;
    }

    public ReportCmdExecutionPlan<R> build() {
      final GroupByPart<R> groupByPart = context.getBean(this.groupByPartClass);
      final ViewPart viewPart = context.getBean(this.viewPartClass);
      return new ReportCmdExecutionPlan<>(
        groupByPart,
        viewPart,
        esClient,
        processDefinitionReader,
        processQueryFilterEnhancer
      );
    }
  }

}
