/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.DIGEST;
import static io.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.ENABLED;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.ProcessRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessRepositoryES implements ProcessRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ProcessRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public ProcessRepositoryES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(
      final Set<String> processDefinitionKeys) {
    LOG.debug("Fetching process overviews for [{}] processes", processDefinitionKeys.size());
    if (processDefinitionKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                    .query(q -> q.ids(i -> i.values(processDefinitionKeys.stream().toList())))
                    .size(LIST_FETCH_LIMIT));
    final SearchResponse<ProcessOverviewDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, ProcessOverviewDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch overviews for processes [%s].", processDefinitionKeys);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
            searchResponse.hits(), ProcessOverviewDto.class, objectMapper)
        .stream()
        .collect(
            Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }

  @Override
  public Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey() {
    LOG.debug("Fetching all available process overviews.");

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                    .query(
                        q ->
                            q.bool(
                                bb ->
                                    bb.must(
                                        m ->
                                            m.term(
                                                t -> t.field(DIGEST + "." + ENABLED).value(true)))))
                    .size(LIST_FETCH_LIMIT));

    final SearchResponse<ProcessOverviewDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, ProcessOverviewDto.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch process overviews.";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
            searchResponse.hits(), ProcessOverviewDto.class, objectMapper)
        .stream()
        .collect(
            Collectors.toMap(
                ProcessOverviewDto::getProcessDefinitionKey, ProcessOverviewDto::getDigest));
  }

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData() {
    LOG.debug("Fetching pending process overviews");

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                    .query(
                        q ->
                            q.prefix(
                                p ->
                                    p.field(ProcessOverviewDto.Fields.processDefinitionKey)
                                        .value("pendingauthcheck"))));

    final SearchResponse<ProcessOverviewDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, ProcessOverviewDto.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch pending processes";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
            searchResponse.hits(), ProcessOverviewDto.class, objectMapper)
        .stream()
        .collect(
            Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }
}
