/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByFlowNode extends ProcessGroupByPart {

  private static final String NESTED_EVENTS_AGGREGATION = "nestedEvents";
  private static final String FLOW_NODES_AGGREGATION = "flowNodes";
  private static final String FILTERED_FLOW_NODES_AGGREGATION = "filteredFlowNodes";

  private static final String MI_BODY = "multiInstanceBody";

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final FlowNodeExecutionState flowNodeExecutionState = context.getReportConfiguration().getFlowNodeExecutionState();
    final NestedAggregationBuilder groupByAssigneeAggregation =
      nested(FLOW_NODES_AGGREGATION, EVENTS)
        .subAggregation(
          filter(
            FILTERED_FLOW_NODES_AGGREGATION,
            addExecutionStateFilter(
              boolQuery()
                .mustNot(
                  termQuery(EVENTS + "." + ACTIVITY_TYPE, MI_BODY)
                ),
              flowNodeExecutionState,
              EVENTS + "." + ACTIVITY_END_DATE
            )
          )
            .subAggregation(
              terms(NESTED_EVENTS_AGGREGATION)
                .size(configurationService.getEsAggregationBucketLimit())
                .field(EVENTS + "." + ACTIVITY_ID)
                .subAggregation(distributedByPart.createAggregation(context))
            )
        );
    return Collections.singletonList(groupByAssigneeAggregation);
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(final SearchResponse response,
                                                    final ExecutionContext<ProcessReportDataDto> context) {

    final Aggregations aggregations = response.getAggregations();
    final Nested flowNodes = aggregations.get(FLOW_NODES_AGGREGATION);
    final Filter filteredUserTasks = flowNodes.getAggregations().get(FILTERED_FLOW_NODES_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(NESTED_EVENTS_AGGREGATION);

    final Map<String, String> flowNodeNames = getFlowNodeNames(context.getReportData());
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (Terms.Bucket flowNodeBucket : byTaskIdAggregation.getBuckets()) {
      final String flowNodeKey = flowNodeBucket.getKeyAsString();
      if (flowNodeNames.containsKey(flowNodeKey)) {
        final List<DistributedByResult> singleResult =
          distributedByPart.retrieveResult(response, flowNodeBucket.getAggregations(), context);
        String label = flowNodeNames.get(flowNodeKey);
        groupedData.add(GroupByResult.createGroupByResult(flowNodeKey, label, singleResult));
        flowNodeNames.remove(flowNodeKey);
      }
    }

    // enrich data with flow nodes that haven't been executed, but should still show up in the result
    flowNodeNames.keySet().forEach(flowNodeKey -> {
      GroupByResult emptyResult = GroupByResult.createResultWithEmptyValue(flowNodeKey);
      emptyResult.setLabel(flowNodeNames.get(flowNodeKey));
      groupedData.add(emptyResult);
    });

    CompositeCommandResult compositeCommandResult = new CompositeCommandResult();
    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(byTaskIdAggregation.getSumOfOtherDocCounts() == 0L);

    return compositeCommandResult;
  }

  private Map<String, String> getFlowNodeNames(final ProcessReportDataDto reportData) {
    return processDefinitionReader.getProcessDefinitionIfAvailable(reportData)
      .orElse(new ProcessDefinitionOptimizeDto())
      .getFlowNodeNames();
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new FlowNodesGroupByDto());
  }

}
