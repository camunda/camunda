/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
class ProcessInstanceRepositoryES implements ProcessInstanceRepository {
  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;
  private final TaskRepositoryES taskRepositoryES;

  @Override
  public void deleteByIds(
      final String index, final String itemName, final List<String> processInstanceIds) {
    final BulkRequest bulkRequest =
        BulkRequest.of(
            b ->
                b.operations(
                    processInstanceIds.stream()
                        .map(
                            id ->
                                BulkOperation.of(
                                    o ->
                                        o.delete(
                                            d ->
                                                d.id(id)
                                                    .index(
                                                        esClient
                                                            .addPrefixesToIndices(index)
                                                            .get(0)))))
                        .toList()));
    esClient.doBulkRequest(bulkRequest, index, false);
  }

  @Override
  public void bulkImport(
      final String bulkRequestName, final List<ImportRequestDto> importRequests) {
    esClient.executeImportRequestsAsBulk(
        bulkRequestName,
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public boolean processDefinitionHasStartedInstances(final String processDefinitionKey) {
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, getProcessInstanceIndexAliasName(processDefinitionKey))
                    .query(
                        q ->
                            q.bool(
                                bb ->
                                    bb.filter(
                                        f ->
                                            f.exists(
                                                e -> e.field(ProcessInstanceIndex.START_DATE)))))
                    .size(1)
                    .source(s -> s.filter(f -> f.includes(PROCESS_INSTANCE_ID))));
    final SearchResponse<?> response;
    try {
      response = esClient.search(searchRequest, Object.class);
      return !response.hits().hits().isEmpty();
    } catch (final ElasticsearchException e) {
      // If the index doesn't exist yet, then this exception is thrown. No need to worry, just
      // return false
      return false;
    } catch (final IOException e2) {
      // If this exception is thrown, sth went wrong with ElasticSearch, so returning false and
      // logging it
      log.warn(
          "Error with ElasticSearch thrown while querying for started process instances, returning false! The "
              + "error was: "
              + e2.getMessage());
      return false;
    }
  }

  @Override
  public PageResultDto<String> getNextPageOfProcessInstanceIds(
      final PageResultDto<String> previousPage,
      final Supplier<PageResultDto<String>> firstPageFetchFunction) {
    if (previousPage.isLastPage()) {
      return new PageResultDto<>(previousPage.getLimit());
    }
    try {
      return ElasticsearchReaderUtil.retrieveNextScrollResultsPage(
          previousPage.getPagingState(),
          String.class,
          searchHit ->
              objectMapper
                  .convertValue(searchHit.source(), ProcessInstanceDto.class)
                  .getProcessInstanceId(),
          esClient,
          configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
          previousPage.getLimit());
    } catch (final ElasticsearchException e) {
      if (e.status() == NOT_FOUND.code()) {
        // this error occurs when the scroll id expired in the meantime, thus just restart it
        return firstPageFetchFunction.get();
      }
      throw e;
    }
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
        processDefinitionKey,
        BoolQuery.of(
            b ->
                b.filter(
                        q ->
                            q.range(
                                r ->
                                    r.date(
                                        d ->
                                            d.field(END_DATE)
                                                .lt(dateTimeFormatter.format(endDate)))))
                    .filter(
                        f ->
                            f.nested(
                                n ->
                                    n.path(VARIABLES)
                                        .scoreMode(ChildScoreMode.None)
                                        .query(
                                            q ->
                                                q.exists(
                                                    e ->
                                                        e.field(VARIABLES + "." + VARIABLE_ID)))))),
        limit);
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
        processDefinitionKey,
        BoolQuery.of(
            b ->
                b.filter(
                    q ->
                        q.range(
                            r ->
                                r.date(
                                    d ->
                                        d.field(END_DATE).lt(dateTimeFormatter.format(endDate)))))),
        limit);
  }

  private PageResultDto<String> getFirstPageOfProcessInstanceIdsForFilter(
      final String processDefinitionKey, final BoolQuery filterQuery, final Integer limit) {
    final PageResultDto<String> result = new PageResultDto<>(limit);
    final Integer resolvedLimit = Optional.ofNullable(limit).orElse(MAX_RESPONSE_SIZE_LIMIT);

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, getProcessInstanceIndexAliasName(processDefinitionKey))
                    .query(q -> q.bool(filterQuery))
                    .source(s -> s.filter(f -> f.includes(List.of(PROCESS_INSTANCE_ID))))
                    .scroll(
                        Time.of(
                            t ->
                                t.time(
                                    configurationService
                                            .getElasticSearchConfiguration()
                                            .getScrollTimeoutInSeconds()
                                        + "s")))
                    // size of each scroll page, needs to be capped to max size of elasticsearch
                    .size(
                        resolvedLimit <= MAX_RESPONSE_SIZE_LIMIT
                            ? resolvedLimit
                            : MAX_RESPONSE_SIZE_LIMIT));

    try {
      final SearchResponse<Map> response = esClient.search(searchRequest, Map.class);
      result
          .getEntities()
          .addAll(
              ElasticsearchReaderUtil.mapHits(
                  response.hits(),
                  resolvedLimit,
                  String.class,
                  searchHit -> ((Map) searchHit.source()).get(PROCESS_INSTANCE_ID).toString()));
      result.setPagingState(response.scrollId());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not obtain process instance ids.", e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to obtain process instance IDs because instance index {} does not exist. Returning empty result.",
            getProcessInstanceIndexAliasName(processDefinitionKey));
        result.setPagingState(null);
        return result;
      }
      throw e;
    }

    return result;
  }

  private ImmutableMap<String, String> createUpdateStateScriptParamsMap(final String newState) {
    return ImmutableMap.of(
        "activeState", ACTIVE_STATE,
        "suspendedState", SUSPENDED_STATE,
        "newState", newState);
  }
}
