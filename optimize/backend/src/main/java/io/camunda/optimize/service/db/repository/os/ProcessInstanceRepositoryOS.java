/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.json;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.sourceInclude;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static java.lang.Math.min;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
class ProcessInstanceRepositoryOS implements ProcessInstanceRepository {

  public static final String INDEX_NOT_FOUND_ERROR_MESSAGE_KEYWORD = "index_not_found_exception";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessInstanceRepositoryOS.class);
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public ProcessInstanceRepositoryOS(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final OptimizeOpenSearchClient osClient,
      final ObjectMapper objectMapper,
      final DateTimeFormatter dateTimeFormatter) {
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.osClient = osClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public void deleteByIds(
      final String index, final String itemName, final List<String> processInstanceIds) {
    final List<BulkOperation> bulkOperations =
        processInstanceIds.stream()
            .map(
                id ->
                    BulkOperation.of(
                        op ->
                            op.delete(
                                d -> d.index(osClient.convertToPrefixedAliasName(index)).id(id))))
            .toList();

    osClient.doBulkRequest(BulkRequest.Builder::new, bulkOperations, itemName, false);
  }

  @Override
  public void bulkImport(
      final String bulkRequestName, final List<ImportRequestDto> importRequests) {
    osClient.executeImportRequestsAsBulk(
        bulkRequestName,
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public boolean processDefinitionHasStartedInstances(final String processDefinitionKey) {
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(
                    getProcessInstanceIndexAliasName(processDefinitionKey)))
            .query(exists(ProcessInstanceIndex.START_DATE))
            .source(sourceInclude(PROCESS_INSTANCE_ID))
            .size(1);

    try {
      return osClient
              .search(
                  requestBuilder, Object.class, "Failed querying for started process instances!")
              .hits()
              .total()
              .value()
          > 0;
    } catch (final OpenSearchException e) {
      // If the index doesn't exist yet, then this exception is thrown. No need to worry, just
      // return false
      return false;
    }
  }

  @Override
  public PageResultDto<String> getNextPageOfProcessInstanceIds(
      final PageResultDto<String> previousPage,
      final Supplier<PageResultDto<String>> firstPageFetchFunction) {
    record Result(String processInstanceId) {}

    final int limit = previousPage.getLimit();
    if (previousPage.isLastPage()) {
      return new PageResultDto<>(limit);
    }
    try {
      final PageResultDto<String> pageResult = new PageResultDto<>(limit);
      String currentScrollId = previousPage.getPagingState();
      boolean limitReached = false;
      List<String> processInstanceIds = List.of();
      do {
        if (pageResult.getEntities().size() < limit) {
          final ScrollResponse<Result> response =
              osClient.scroll(currentScrollId, scrollTimeout(), Result.class);
          currentScrollId = response.scrollId();
          processInstanceIds =
              response.hits().hits().stream().map(hit -> hit.source().processInstanceId()).toList();
          pageResult
              .getEntities()
              .addAll(
                  processInstanceIds.subList(
                      0,
                      min(response.hits().hits().size(), limit - pageResult.getEntities().size())));
          pageResult.setPagingState(currentScrollId);
        } else {
          limitReached = true;
        }
      } while (!limitReached && !processInstanceIds.isEmpty());

      if (pageResult.getEntities().isEmpty() || pageResult.getEntities().size() < limit) {
        osClient.clearScroll(
            currentScrollId,
            e ->
                format(
                    "Could not clear scroll for class [%s], since Opensearch was unable to perform the action!",
                    getClass().getSimpleName()));
        pageResult.setPagingState(null);
      }

      return pageResult;

    } catch (final OpenSearchException e) {
      if (HttpStatus.NOT_FOUND.value() == e.response().status()) {
        // this error occurs when the scroll id expired in the meantime, thus just restart it
        return firstPageFetchFunction.get();
      }
      throw e;
    } catch (final IOException e) {
      final String reason =
          format("Could not close scroll for class [%s].", getClass().getSimpleName());
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
        processDefinitionKey,
        and(
            lt(END_DATE, dateTimeFormatter.format(endDate)),
            nested(VARIABLES, exists(VARIABLES + "." + VARIABLE_ID), ChildScoreMode.None)),
        limit);
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(
      final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
        processDefinitionKey, lt(END_DATE, dateTimeFormatter.format(endDate)), limit);
  }

  private PageResultDto<String> getFirstPageOfProcessInstanceIdsForFilter(
      final String processDefinitionKey, final Query filterQuery, final Integer limit) {
    record Result(String processInstanceId) {}

    final PageResultDto<String> result = new PageResultDto<>(limit);
    final Integer resolvedLimit = Optional.ofNullable(limit).orElse(MAX_RESPONSE_SIZE_LIMIT);

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(
                    getProcessInstanceIndexAliasName(processDefinitionKey)))
            .scroll(builder -> builder.time(scrollTimeout()))
            .query(filterQuery)
            .source(sourceInclude(PROCESS_INSTANCE_ID))
            // size of each scroll page, needs to be capped to max size of opensearch
            .size(
                resolvedLimit <= MAX_RESPONSE_SIZE_LIMIT ? resolvedLimit : MAX_RESPONSE_SIZE_LIMIT);
    try {
      final SearchResponse<Result> response =
          osClient.search(requestBuilder, Result.class, "Could not obtain process instance ids.");
      final List<String> processInstanceIds =
          response.hits().hits().stream().map(hit -> hit.source().processInstanceId()).toList();
      if (!processInstanceIds.isEmpty()) {
        result
            .getEntities()
            .addAll(
                processInstanceIds.subList(0, min(response.hits().hits().size(), resolvedLimit)));
      }
      result.setPagingState(response.scrollId());
    } catch (final OpenSearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to obtain process instance IDs because instance index {} does not exist. Returning empty result.",
            getProcessInstanceIndexAliasName(processDefinitionKey));
        result.setPagingState(null);
        return result;
      }
      throw e;
    }

    return result;
  }

  private String scrollTimeout() {
    return format(
        "%ss", configurationService.getOpenSearchConfiguration().getScrollTimeoutInSeconds());
  }

  private Script createUpdateStateScript(final String newState) {
    final Map<String, JsonData> scriptParameters = createUpdateStateScriptParamsMap(newState);
    return createDefaultScriptWithPrimitiveParams(
        ProcessInstanceScriptFactory.createInlineUpdateScript(), scriptParameters);
  }

  private Map<String, JsonData> createUpdateStateScriptParamsMap(final String newState) {
    return Map.of(
        "activeState", json(ACTIVE_STATE),
        "suspendedState", json(SUSPENDED_STATE),
        "newState", json(newState));
  }

  private String aliasForProcessDefinitionKey(final String processDefinitionKey) {
    return indexNameService.getOptimizeIndexAliasForIndex(
        getProcessInstanceIndexAliasName(processDefinitionKey));
  }
}
