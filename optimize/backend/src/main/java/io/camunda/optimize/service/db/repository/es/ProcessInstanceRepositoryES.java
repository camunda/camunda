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
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static java.lang.String.format;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
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

  @Override
  public void bulkImportProcessInstances(
      final String importItemName, final List<ProcessInstanceDto> processInstanceDtos) {
    doImportBulkRequestWithList(
        importItemName,
        processInstanceDtos,
        (request, dto) ->
            addImportProcessInstanceRequest(
                getProcessInstanceIndexAliasName(dto.getProcessDefinitionKey()),
                request,
                dto,
                createUpdateStateScript(dto.getState()),
                objectMapper));
  }

  @Override
  public void updateProcessInstanceStateForProcessDefinitionId(
      final String importItemName,
      final String definitionKey,
      final String processDefinitionId,
      final String state) {
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        format(
            "%s with %s: %s",
            importItemName, ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
        createUpdateStateScript(state),
        termsQuery(ProcessInstanceDto.Fields.processDefinitionId, processDefinitionId),
        getProcessInstanceIndexAliasName(definitionKey));
  }

  @Override
  public void updateAllProcessInstancesStates(
      final String importItemName, final String definitionKey, final String state) {
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        format(
            "%s with %s: %s",
            importItemName, ProcessInstanceDto.Fields.processDefinitionKey, definitionKey),
        createUpdateStateScript(state),
        matchAllQuery(),
        getProcessInstanceIndexAliasName(definitionKey));
  }

  @Override
  public void deleteByIds(
      final String index, final String itemName, final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest();
    processInstanceIds.forEach(id -> bulkRequest.add(new DeleteRequest(index, id)));
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
  public void deleteEndedBefore(
      final String index, final OffsetDateTime endDate, final String deletedItemIdentifier) {
    final BoolQueryBuilder filterQuery =
        boolQuery().filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)));
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient, filterQuery, deletedItemIdentifier, false, index);
  }

  @Override
  public void deleteVariablesOfInstancesThatEndedBefore(
      final String index, final OffsetDateTime endDate, final String updateItem) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)))
            .filter(
                nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None));
    final Script script = new Script(ProcessInstanceScriptFactory.createVariableClearScript());
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient, updateItem, script, filterQuery, index);
  }

  @Override
  public boolean processDefinitionHasStartedInstances(final String processDefinitionKey) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(boolQuery().filter(existsQuery(ProcessInstanceIndex.START_DATE)))
            .size(1)
            .fetchSource(PROCESS_INSTANCE_ID, null);

    final SearchRequest searchRequest =
        new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionKey))
            .source(searchSourceBuilder);
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest);
      return response.getHits().getHits().length > 0;
    } catch (final ElasticsearchStatusException e) {
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
          searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID),
          esClient,
          configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
          previousPage.getLimit());
    } catch (final ElasticsearchStatusException e) {
      if (RestStatus.NOT_FOUND.equals(e.status())) {
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
        boolQuery()
            .filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate)))
            .filter(
                nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None)),
        limit);
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
        processDefinitionKey,
        boolQuery().filter(rangeQuery(END_DATE).lt(dateTimeFormatter.format(endDate))),
        limit);
  }

  private PageResultDto<String> getFirstPageOfProcessInstanceIdsForFilter(
      final String processDefinitionKey, final BoolQueryBuilder filterQuery, final Integer limit) {
    final PageResultDto<String> result = new PageResultDto<>(limit);
    final Integer resolvedLimit = Optional.ofNullable(limit).orElse(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(filterQuery)
            .fetchSource(PROCESS_INSTANCE_ID, null)
            // size of each scroll page, needs to be capped to max size of elasticsearch
            .size(
                resolvedLimit <= MAX_RESPONSE_SIZE_LIMIT ? resolvedLimit : MAX_RESPONSE_SIZE_LIMIT);

    final SearchRequest scrollSearchRequest =
        new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionKey))
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    try {
      final SearchResponse response = esClient.search(scrollSearchRequest);
      result
          .getEntities()
          .addAll(
              ElasticsearchReaderUtil.mapHits(
                  response.getHits(),
                  resolvedLimit,
                  String.class,
                  searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID)));
      result.setPagingState(response.getScrollId());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not obtain process instance ids.", e);
    } catch (final ElasticsearchStatusException e) {
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

  private <T> void doImportBulkRequestWithList(
      final String importItemName,
      final List<T> processInstanceDtos,
      final BiConsumer<BulkRequest, T> addDtoToRequestConsumer) {
    esClient.doImportBulkRequestWithList(
        importItemName,
        processInstanceDtos,
        addDtoToRequestConsumer,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addImportProcessInstanceRequest(
      final String index,
      final BulkRequest bulkRequest,
      final ProcessInstanceDto processInstanceDto,
      final Script updateScript,
      final ObjectMapper objectMapper) {
    bulkRequest.add(createUpdateRequestDto(index, processInstanceDto, updateScript, objectMapper));
  }

  private UpdateRequest createUpdateRequestDto(
      final String index,
      final ProcessInstanceDto processInstanceDto,
      final Script updateScript,
      final ObjectMapper objectMapper) {
    final String newEntryIfAbsent;
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(processInstanceDto);
    } catch (final JsonProcessingException e) {
      final String reason =
          String.format(
              "Error while processing JSON for process instance DTO with ID [%s].",
              processInstanceDto.getProcessInstanceId());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return new UpdateRequest()
        .index(index)
        .id(processInstanceDto.getProcessInstanceId())
        .script(updateScript)
        .scriptedUpsert(true)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }

  private Script createUpdateStateScript(final String newState) {
    final ImmutableMap<String, Object> scriptParameters =
        createUpdateStateScriptParamsMap(newState);
    return ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
        ProcessInstanceScriptFactory.createInlineUpdateScript(), scriptParameters);
  }

  private ImmutableMap<String, Object> createUpdateStateScriptParamsMap(final String newState) {
    return ImmutableMap.of(
        "activeState", ACTIVE_STATE,
        "suspendedState", SUSPENDED_STATE,
        "newState", newState);
  }
}
