/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Slf4j
public class ProcessReportCmdExecutionPlan<R extends ProcessReportResultDto>
  extends ReportCmdExecutionPlan<R, ProcessReportDataDto> {

  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessQueryFilterEnhancer queryFilterEnhancer;

  public ProcessReportCmdExecutionPlan(final ViewPart<ProcessReportDataDto> viewPart,
                                       final GroupByPart<ProcessReportDataDto> groupByPart,
                                       final DistributedByPart<ProcessReportDataDto> distributedByPart,
                                       final Function<CompositeCommandResult, R> mapToReportResult,
                                       final OptimizeElasticsearchClient esClient,
                                       final ProcessDefinitionReader processDefinitionReader,
                                       final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    super(viewPart, groupByPart, distributedByPart, mapToReportResult, esClient);
    this.processDefinitionReader = processDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  protected BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto reportData) {
    BoolQueryBuilder boolQueryBuilder = createDefinitionQuery(
      reportData.getDefinitionKey(),
      reportData.getDefinitionVersions(),
      reportData.getTenantIds(),
      new ProcessInstanceIndex(),
      processDefinitionReader::getLatestVersionToKey
    );
    queryFilterEnhancer.addFilterToQuery(boolQueryBuilder, reportData.getFilter());
    return boolQueryBuilder;
  }

  @Override
  protected String getIndexName() {
    return PROCESS_INSTANCE_INDEX_NAME;
  }

  @Override
  protected Supplier<ProcessReportDataDto> getDataDtoSupplier() {
    return ProcessReportDataDto::new;
  }
}
