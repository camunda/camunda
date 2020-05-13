/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.DefinitionQueryUtil.createDefinitionQuery;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Slf4j
public class AssigneeAndCandidateGroupsReader {

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;

  public List<String> getCandidateGroups(AssigneeRequestDto requestDto) {
    return getSearchResponse(requestDto, ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS);
  }

  public List<String> getAssignees(AssigneeRequestDto requestDto) {
    return getSearchResponse(requestDto, ProcessInstanceIndex.USER_TASK_ASSIGNEE);
  }

  public List<String> getSearchResponse(AssigneeRequestDto requestDto, String field) {
    if (requestDto.getProcessDefinitionVersions() == null || requestDto.getProcessDefinitionVersions().isEmpty()) {
      log.debug("Cannot fetch {} for process definition with missing versions.", field);
      return Collections.emptyList();
    }

    log.debug(
      "Fetching {} for process definition with key [{}] and versions [{}] and tenants [{}]",
      field,
      requestDto.getProcessDefinitionKey(),
      requestDto.getProcessDefinitionVersions(),
      requestDto.getTenantIds()
    );

    BoolQueryBuilder query =
      createDefinitionQuery(
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions(),
        requestDto.getTenantIds(),
        new ProcessInstanceIndex(),
        processDefinitionReader::getLatestVersionToKey
      );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    TermsAggregationBuilder aggregation = terms(field)
      .field(ProcessInstanceIndex.USER_TASKS + "." + field)
      .size(10_000)
      .order(BucketOrder.key(true));
    searchSourceBuilder.aggregation(
      nested(ProcessInstanceIndex.USER_TASKS, ProcessInstanceIndex.USER_TASKS)
        .subAggregation(
          aggregation
        )
    );


    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch %s for definition with key [%s] and versions [%s]",
        field,
        requestDto.getProcessDefinitionKey(),
        requestDto.getProcessDefinitionVersions()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    Aggregations aggregations = searchResponse.getAggregations();
    return extractValues(aggregations, field);
  }

  private List<String> extractValues(Aggregations aggregations, String field) {
    Nested userTasksAgg = aggregations.get(ProcessInstanceIndex.USER_TASKS);
    Terms candidateGroupsBuckets = userTasksAgg.getAggregations().get(field);
    return candidateGroupsBuckets.getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKeyAsString)
      .collect(Collectors.toList());
  }
}
