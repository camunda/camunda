/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessOverviewIndex.DIGEST;
import static org.camunda.optimize.service.es.schema.index.ProcessOverviewIndex.ENABLED;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(final Set<String> processDefinitionKeys) {
    log.debug("Fetching process overviews for [{}] processes", processDefinitionKeys.size());
    if (processDefinitionKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
      .query(QueryBuilders.idsQuery().addIds(processDefinitionKeys.toArray(new String[0])))
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(PROCESS_OVERVIEW_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch overviews for processes [%s].", processDefinitionKeys);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), ProcessOverviewDto.class, objectMapper)
      .stream()
      .collect(Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }

  public Optional<ProcessOverviewDto> getProcessOverviewByKey(final String processDefinitionKey) {
    final Map<String, ProcessOverviewDto> goalsForProcessesByKey =
      getProcessOverviewsByKey(Collections.singleton(processDefinitionKey));
    return Optional.ofNullable(goalsForProcessesByKey.get(processDefinitionKey));
  }

  public Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey() {
    log.debug("Fetching all available process overviews.");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
      .query(boolQuery().must(termQuery(DIGEST + "." + ENABLED, true)))
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(PROCESS_OVERVIEW_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = "Was not able to fetch process overviews.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), ProcessOverviewDto.class, objectMapper)
      .stream()
      .collect(Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, ProcessOverviewDto::getDigest));
  }

  public Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData() {
    log.debug("Fetching pending process overviews");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
      .query(QueryBuilders.prefixQuery(ProcessOverviewDto.Fields.processDefinitionKey, "pendingauthcheck"))
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(PROCESS_OVERVIEW_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch pending processes";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(),
        ProcessOverviewDto.class,
        objectMapper
      )
      .stream()
      .collect(Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }
}
