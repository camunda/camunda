/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToValidDefinitionVersion;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.hasMultipleVersionsSet;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GroupByUserTask extends GroupByPart<ReportMapResultDto> {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "tasks";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ProcessReportDataDto definitionData) {
    final FlowNodeExecutionState flowNodeExecutionState = definitionData.getConfiguration().getFlowNodeExecutionState();
    final NestedAggregationBuilder groupByAssigneeAggregation = nested(USER_TASKS, USER_TASKS_AGGREGATION)
      .subAggregation(
        filter(
          FILTERED_USER_TASKS_AGGREGATION,
          addExecutionStateFilter(
            boolQuery(),
            flowNodeExecutionState,
            USER_TASKS + "." + USER_TASK_END_DATE
          )
        )
          .subAggregation(
            AggregationBuilders
              .terms(USER_TASK_ID_TERMS_AGGREGATION)
              .size(configurationService.getEsAggregationBucketLimit())
              .field(USER_TASKS + "." + USER_TASK_ACTIVITY_ID)
              .subAggregation(viewPart.createAggregation(definitionData))
          )
      );
    return Collections.singletonList(groupByAssigneeAggregation);
  }

  @Override
  protected ReportMapResultDto retrieveResult(final SearchResponse response, final ProcessReportDataDto reportData) {
    final ReportMapResultDto resultDto = new ReportMapResultDto();

    final Aggregations aggregations = response.getAggregations();
    final Nested userTasks = aggregations.get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byTaskIdAggregation = filteredUserTasks.getAggregations().get(USER_TASK_ID_TERMS_AGGREGATION);

    final List<MapResultEntryDto> resultData = new ArrayList<>();
    for (Terms.Bucket b : byTaskIdAggregation.getBuckets()) {
      final Long singleResult = viewPart.retrieveResult(b.getAggregations(), reportData);
      resultData.add(new MapResultEntryDto(b.getKeyAsString(), singleResult));
    }

    resultDto.setData(resultData);
    resultDto.setIsComplete(byTaskIdAggregation.getSumOfOtherDocCounts() == 0L);
    resultDto.setInstanceCount(response.getHits().getTotalHits());

    return resultDto;
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new FlowNodesGroupByDto());
  }

  @Override
  public void sortResultData(final ProcessReportDataDto reportData, final ReportMapResultDto resultDto) {
    reportData.getConfiguration().getSorting().ifPresent(
      sorting -> MapResultSortingUtility.sortResultData(sorting, resultDto)
    );
  }

  @Override
  protected ReportMapResultDto filterResultData(final ProcessReportDataDto reportData,
                                                final ReportMapResultDto resultDto) {
    // if version is set to all, filter for only latest flow nodes
    List<String> versions = reportData.getDefinitionVersions();
    if (isDefinitionVersionSetToAll(versions) || hasMultipleVersionsSet(versions)) {
      getProcessDefinitionIfAvailable(reportData)
        .ifPresent(processDefinition -> {
          final Map<String, String> flowNodeNames = processDefinition.getFlowNodeNames();

          final List<MapResultEntryDto> collect = resultDto
            .getData()
            .stream()
            .filter(resultEntry -> flowNodeNames.containsKey(resultEntry.getKey()))
            .collect(Collectors.toList());

          resultDto.setData(collect);
        });
    }
    return resultDto;
  }

  @Override
  protected ReportMapResultDto enrichResultData(final ProcessReportDataDto reportData,
                                                final ReportMapResultDto resultDto) {
    // We are enriching the Result Data with not executed flow nodes.
    // For those flow nodes, count value is set to null.
    // A value of 0 doesn't work, because the heatmap shows still a little heat for a 0 value.
    enrichResultData(
      reportData, resultDto, () -> null, ProcessDefinitionOptimizeDto::getUserTaskNames
    );
    return resultDto;
  }

  private void enrichResultData(
    final ProcessReportDataDto reportData,
    final ReportMapResultDto resultDto,
    final Supplier<Long> createNewEmptyResult,
    final Function<ProcessDefinitionOptimizeDto, Map<String, String>> flowNodeNameExtractor) {

    getProcessDefinitionIfAvailable(reportData)
      .ifPresent(processDefinition -> {
        final Map<String, String> flowNodeNames = flowNodeNameExtractor.apply(processDefinition);

        Set<String> flowNodeKeysWithResult = new HashSet<>();
        resultDto.getData().forEach(entry -> {
          entry.setLabel(flowNodeNames.get(entry.getKey()));
          flowNodeKeysWithResult.add(entry.getKey());
        });
        Set<String> allFlowNodeKeys = flowNodeNames.keySet();
        Set<String> difference = Sets.difference(allFlowNodeKeys, flowNodeKeysWithResult);
        difference.forEach(flowNodeKey -> {
          MapResultEntryDto emptyResult = new MapResultEntryDto(flowNodeKey, createNewEmptyResult.get());
          emptyResult.setLabel(flowNodeNames.get(flowNodeKey));
          resultDto.getData().add(emptyResult);
        });

      });
  }

  private Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionIfAvailable(
    final ProcessReportDataDto reportData) {

    String mostRecentValidVersion = convertToValidDefinitionVersion(
      reportData.getDefinitionKey(),
      reportData.getDefinitionVersions(),
      processDefinitionReader::getLatestVersionToKey
    );
    return this.processDefinitionReader
      .getFullyImportedProcessDefinition(
        reportData.getProcessDefinitionKey(),
        mostRecentValidVersion,
        reportData.getTenantIds().stream()
          // to get a null value if the first element is either absent or null
          .map(Optional::ofNullable).findFirst().flatMap(Function.identity())
          .orElse(null)
      );
  }

}
