/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.result.process.SingleProcessNumberReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

public abstract class AbstractProcessInstanceDurationGroupByNoneCommand
  extends ProcessReportCommand<SingleProcessNumberReportResult> {

  protected AggregationStrategy aggregationStrategy;

  @Override
  protected SingleProcessNumberReportResult evaluate() {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating process instance duration grouped by none report " +
        "for process definition key [{}] and versions [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersions()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .size(0);
    addAggregation(searchSourceBuilder);

    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate process instance duration grouped by none report " +
            "for process definition key [%s] and versions [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersions()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    Aggregations aggregations = response.getAggregations();

    NumberResultDto numberResultDto = new NumberResultDto();
    numberResultDto.setData(processAggregationOperation(aggregations));
    numberResultDto.setInstanceCount(response.getHits().getTotalHits());
    return new SingleProcessNumberReportResult(numberResultDto, reportDefinition);
  }

  @Override
  protected void sortResultData(
    final SingleProcessNumberReportResult evaluationResult) {
    // no ordering for single result
  }

  private void addAggregation(SearchSourceBuilder searchSourceBuilder) {
    searchSourceBuilder.aggregation(createOperationsAggregation());
  }

  protected abstract Long processAggregationOperation(Aggregations aggregations);

  protected abstract AggregationBuilder createOperationsAggregation();

}
