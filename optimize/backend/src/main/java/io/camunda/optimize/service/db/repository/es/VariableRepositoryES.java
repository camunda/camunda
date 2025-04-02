/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.INGESTION_TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex.TIMESTAMP;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.util.LogUtil.sanitizeLogMessage;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.DoubleTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DoubleTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeMultiGetOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import io.camunda.optimize.service.db.util.ProcessVariableHelper;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class VariableRepositoryES implements VariableRepository {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final ProcessQueryFilterEnhancerES processQueryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;
  private final TaskRepositoryES taskRepositoryES;

  @Override
  public void deleteVariableDataByProcessInstanceIds(
      final String processDefinitionKey, final List<String> processInstanceIds) {
    final BulkRequest bulkRequest =
        BulkRequest.of(
            b -> {
              b.refresh(Refresh.True);
              processInstanceIds.forEach(
                  id ->
                      b.operations(
                          o ->
                              o.update(
                                  OptimizeUpdateOperationBuilderES.of(
                                      u ->
                                          u.optimizeIndex(
                                                  esClient,
                                                  getProcessInstanceIndexAliasName(
                                                      processDefinitionKey))
                                              .id(id)
                                              .action(
                                                  a ->
                                                      a.script(
                                                          Script.of(
                                                              i ->
                                                                  i.source(
                                                                      ProcessInstanceScriptFactory
                                                                          .createVariableClearScript()))))
                                              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)))));
              return b;
            });
    esClient.doBulkRequest(
        bulkRequest, getProcessInstanceIndexAliasName(processDefinitionKey), false);
  }

  @Override
  public void upsertVariableLabel(
      final String variableLabelIndexName,
      final DefinitionVariableLabelsDto definitionVariableLabelsDto,
      final ScriptData scriptData) {
    final Script updateEntityScript =
        createDefaultScriptWithSpecificDtoParams(scriptData.scriptString(), scriptData.params());
    try {
      final UpdateRequest<DefinitionVariableLabelsDto, ?> updateRequest =
          OptimizeUpdateRequestBuilderES.of(
              u ->
                  u.optimizeIndex(esClient, variableLabelIndexName)
                      .id(
                          definitionVariableLabelsDto
                              .getDefinitionKey()
                              .toLowerCase(Locale.ENGLISH))
                      .upsert(definitionVariableLabelsDto)
                      .script(updateEntityScript)
                      .refresh(Refresh.True)
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT));

      esClient.update(updateRequest, DefinitionVariableLabelsDto.class);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update the variable labels for the process definition with id: [%s]",
              definitionVariableLabelsDto.getDefinitionKey());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchException e) {
      final String errorMessage =
          String.format(
              "Was not able to update the variable labels for the process definition with id: [%s] due to an Elasticsearch"
                  + " exception",
              definitionVariableLabelsDto.getDefinitionKey());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteVariablesForDefinition(
      final String variableLabelIndexName, final String processDefinitionKey) {
    final DeleteRequest request =
        OptimizeDeleteRequestBuilderES.of(
            d ->
                d.optimizeIndex(esClient, variableLabelIndexName)
                    .id(processDefinitionKey)
                    .refresh(Refresh.True));
    try {
      esClient.delete(request);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not delete variable label document with id [%s]. ", processDefinitionKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.filter(
                            f ->
                                f.terms(
                                    t ->
                                        t.field(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID)
                                            .terms(
                                                tt ->
                                                    tt.value(
                                                        processInstanceIds.stream()
                                                            .map(FieldValue::of)
                                                            .toList())))))),
        String.format("variable updates of %d process instances", processInstanceIds.size()),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new VariableUpdateInstanceIndexES()));
  }

  @Override
  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      final List<String> processDefinitionKeys) {
    try {
      return esClient
          .mget(
              MgetRequest.of(
                  m -> {
                    processDefinitionKeys.forEach(
                        processDefinitionKey ->
                            m.docs(
                                MultiGetOperation.of(
                                    o ->
                                        new OptimizeMultiGetOperationBuilderES()
                                            .optimizeIndex(esClient, VARIABLE_LABEL_INDEX_NAME)
                                            .id(
                                                processDefinitionKey.toLowerCase(
                                                    Locale.ENGLISH)))));
                    return m;
                  }),
              DefinitionVariableLabelsDto.class)
          .docs()
          .stream()
          .map(this::extractDefinitionLabelsDto)
          .flatMap(Optional::stream)
          .peek(
              label -> label.setDefinitionKey(label.getDefinitionKey().toLowerCase(Locale.ENGLISH)))
          .collect(
              Collectors.toMap(DefinitionVariableLabelsDto::getDefinitionKey, Function.identity()));
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "There was an error while fetching documents from the variable label index with keys %s.",
              processDefinitionKeys);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(
      final Set<String> processInstanceIds) {

    final SearchResponse<VariableUpdateInstanceDto> searchResponse;
    try {
      searchResponse =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(
                              esClient, DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
                          .scroll(
                              c ->
                                  c.time(
                                      configurationService
                                              .getElasticSearchConfiguration()
                                              .getScrollTimeoutInSeconds()
                                          + "s"))
                          .query(
                              q ->
                                  q.bool(
                                      b ->
                                          b.must(
                                              m ->
                                                  m.terms(
                                                      t ->
                                                          t.field(
                                                                  VariableUpdateInstanceIndex
                                                                      .PROCESS_INSTANCE_ID)
                                                              .terms(
                                                                  tt ->
                                                                      tt.value(
                                                                          processInstanceIds
                                                                              .stream()
                                                                              .map(FieldValue::of)
                                                                              .toList()))))))
                          .size(MAX_RESPONSE_SIZE_LIMIT)
                          .sort(ss -> ss.field(f -> f.field(TIMESTAMP).order(SortOrder.Asc)))),
              VariableUpdateInstanceDto.class);
    } catch (final IOException e) {
      log.error("Was not able to retrieve variable instance updates!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve variable instance updates!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        VariableUpdateInstanceDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public void writeExternalProcessVariables(
      final List<ExternalProcessVariableDto> variables, final String itemName) {
    if (!variables.isEmpty()) {
      final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
      variables.forEach(variable -> addInsertExternalVariableRequest(bulkRequestBuilder, variable));

      esClient.doBulkRequest(
          bulkRequestBuilder.build(),
          itemName,
          false // there are no nested documents in the externalProcessVariableIndex
          );
    }
  }

  @Override
  public void deleteExternalVariablesIngestedBefore(
      final OffsetDateTime timestamp, final String deletedItemIdentifier) {
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.filter(
                            f ->
                                f.range(
                                    r ->
                                        r.date(
                                            d ->
                                                d.field(
                                                        ExternalProcessVariableDto.Fields
                                                            .ingestionTimestamp)
                                                    .lt(dateTimeFormatter.format(timestamp))))))),
        deletedItemIdentifier,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new ExternalProcessVariableIndexES()));
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(
      final Long ingestTimestamp, final int limit) {
    return getPageOfVariablesSortedByIngestionTimestamp(
        Query.of(
            q ->
                q.range(
                    r ->
                        r.date(
                            d ->
                                d.field(INGESTION_TIMESTAMP).gt(String.valueOf(ingestTimestamp))))),
        limit);
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(final Long ingestTimestamp) {
    return getPageOfVariablesSortedByIngestionTimestamp(
        Query.of(
            q ->
                q.range(
                    r ->
                        r.date(
                            d ->
                                d.field(INGESTION_TIMESTAMP)
                                    .lte(String.valueOf(ingestTimestamp))
                                    .gte(String.valueOf(ingestTimestamp))))),
        MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public List<String> getDecisionVariableValues(
      final DecisionVariableValueRequestDto requestDto, final String variablesPath) {
    final BoolQuery.Builder definitionQueryBuilder =
        DefinitionQueryUtilES.createDefinitionQuery(
            requestDto.getDecisionDefinitionKey(),
            requestDto.getDecisionDefinitionVersions(),
            requestDto.getTenantIds(),
            new DecisionInstanceIndexES(requestDto.getDecisionDefinitionKey()),
            decisionDefinitionReader::getLatestVersionToKey);
    try {
      final SearchResponse<?> searchResponse =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(
                              esClient,
                              getDecisionInstanceIndexAliasName(
                                  requestDto.getDecisionDefinitionKey()))
                          .query(Query.of(q -> q.bool(definitionQueryBuilder.build())))
                          .aggregations(
                              getDecisionVariableValueAggregation(requestDto, variablesPath))
                          .size(0)),
              Object.class);
      final Map<String, Aggregate> aggregations = searchResponse.aggregations();

      return extractVariableValues(aggregations, requestDto, variablesPath);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getVariableId(), requestDto.getVariableType());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(DECISION, e)) {
        log.info(
            "Was not able to fetch variable values because no instance index with alias {} exists. "
                + "Returning empty list.",
            getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey()));
        return Collections.emptyList();
      }
      throw e;
    }
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableNameRequest,
      final List<ProcessToQueryDto> validNameRequests,
      final List<String> processDefinitionKeys,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    final Supplier<BoolQuery.Builder> builderSupplier =
        () -> {
          final BoolQuery.Builder builder = new BoolQuery.Builder().minimumShouldMatch("1");
          validNameRequests.forEach(
              request ->
                  builder.should(
                      s ->
                          s.bool(
                              b ->
                                  DefinitionQueryUtilES.createDefinitionQuery(
                                      request.getProcessDefinitionKey(),
                                      request.getProcessDefinitionVersions(),
                                      request.getTenantIds(),
                                      new ProcessInstanceIndexES(request.getProcessDefinitionKey()),
                                      processDefinitionReader::getLatestVersionToKey))));

          processQueryFilterEnhancer.addFilterToQuery(
              builder,
              variableNameRequest.getFilter().stream()
                  .filter(filter -> filter.getAppliedTo().contains(APPLIED_TO_ALL_DEFINITIONS))
                  .toList(),
              FilterContext.builder().timezone(variableNameRequest.getTimezone()).build());
          return builder;
        };
    return getVariableNamesForInstancesMatchingQuery(
        processDefinitionKeys, builderSupplier, definitionLabelsDtos);
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final Supplier<BoolQuery.Builder> baseQueryBuilderSupplier,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {

    final String[] indicesToTarget =
        processDefinitionKeysToTarget.stream()
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toArray(String[]::new);
    final BoolQuery boolQuery = baseQueryBuilderSupplier.get().build();
    final Function<Map<String, FieldValue>, SearchRequest> aggregationRequestWithAfterKeys =
        (map) ->
            OptimizeSearchRequestBuilderES.of(
                s ->
                    s.optimizeIndex(esClient, indicesToTarget)
                        .query(q -> q.bool(boolQuery))
                        .size(0)
                        .aggregations(
                            Map.of(
                                VARIABLES,
                                Aggregation.of(
                                    a ->
                                        a.nested(n -> n.path(VARIABLES))
                                            .aggregations(
                                                VAR_NAME_AND_TYPE_COMPOSITE_AGG,
                                                Aggregation.of(
                                                    a1 ->
                                                        a1.composite(
                                                            c -> {
                                                              c.size(
                                                                      configurationService
                                                                          .getElasticSearchConfiguration()
                                                                          .getAggregationBucketLimit())
                                                                  .sources(
                                                                      List.of(
                                                                          Map.of(
                                                                              NAME_AGGREGATION,
                                                                              CompositeAggregationSource
                                                                                  .of(
                                                                                      f ->
                                                                                          f.terms(
                                                                                              t ->
                                                                                                  t.missingBucket(
                                                                                                          false)
                                                                                                      .field(
                                                                                                          getNestedVariableNameField())))),
                                                                          Map.of(
                                                                              TYPE_AGGREGATION,
                                                                              CompositeAggregationSource
                                                                                  .of(
                                                                                      f ->
                                                                                          f.terms(
                                                                                              t ->
                                                                                                  t.missingBucket(
                                                                                                          false)
                                                                                                      .field(
                                                                                                          getNestedVariableTypeField())))),
                                                                          Map.of(
                                                                              INDEX_AGGREGATION,
                                                                              CompositeAggregationSource
                                                                                  .of(
                                                                                      f ->
                                                                                          f.terms(
                                                                                              t ->
                                                                                                  t.missingBucket(
                                                                                                          false)
                                                                                                      .field(
                                                                                                          INDEX_AGGREGATION))))));
                                                              if (map != null) {
                                                                c.after(map);
                                                              }
                                                              return c;
                                                            })))))));

    final List<ProcessVariableNameResponseDto> variableNames = new ArrayList<>();
    ElasticsearchCompositeAggregationScroller.create()
        .setEsClient(esClient)
        .setSearchRequest(aggregationRequestWithAfterKeys.apply(null))
        .setPathToAggregation(VARIABLES, VAR_NAME_AND_TYPE_COMPOSITE_AGG)
        .setFunction(aggregationRequestWithAfterKeys)
        .setCompositeBucketConsumer(
            bucket -> variableNames.add(extractVariableNameAndLabel(bucket, definitionLabelsDtos)))
        .consumeAllPages();
    return filterVariableNameResults(variableNames);
  }

  @Override
  public List<String> getVariableValues(
      final ProcessVariableValuesQueryDto requestDto,
      final List<ProcessVariableSourceDto> processVariableSources) {
    final BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
    processVariableSources.forEach(
        source -> {
          boolBuilder.should(
              s ->
                  s.bool(
                      b ->
                          DefinitionQueryUtilES.createDefinitionQuery(
                              source.getProcessDefinitionKey(),
                              source.getProcessDefinitionVersions(),
                              source.getTenantIds(),
                              new ProcessInstanceIndexES(source.getProcessDefinitionKey()),
                              processDefinitionReader::getLatestVersionToKey)));
          if (source.getProcessInstanceId() != null) {
            boolBuilder.must(
                m ->
                    m.term(
                        t ->
                            t.field(ProcessInstanceIndex.PROCESS_INSTANCE_ID)
                                .value(source.getProcessInstanceId())));
          }
        });

    try {
      final SearchResponse<?> searchResponse =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, PROCESS_INSTANCE_MULTI_ALIAS)
                          .query(q -> q.bool(boolBuilder.build()))
                          .aggregations(getVariableValueAggregation(requestDto))
                          .size(0)),
              Object.class);
      final Map<String, Aggregate> aggregations = searchResponse.aggregations();

      return extractVariableValues(aggregations, requestDto);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getName(), requestDto.getType().getId());
      log.error(sanitizeLogMessage(reason), e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
            "Was not able to fetch variable values because no instance indices exist. Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private ProcessVariableNameResponseDto extractVariableNameAndLabel(
      final CompositeBucket bucket,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsByKey) {
    final String processDefinitionKey =
        extractProcessDefinitionKeyFromIndexName(bucket.key().get(INDEX_AGGREGATION).stringValue());
    final String variableName = bucket.key().get(NAME_AGGREGATION).stringValue();
    final String variableType = bucket.key().get(TYPE_AGGREGATION).stringValue();
    return processVariableNameResponseDtoFrom(
        definitionLabelsByKey, processDefinitionKey, variableName, variableType);
  }

  private List<String> extractVariableValues(
      final Map<String, Aggregate> aggregations, final ProcessVariableValuesQueryDto requestDto) {
    return extractVariableValues(
        aggregations, requestDto.getResultOffset(), requestDto.getNumResults(), VARIABLES);
  }

  private List<String> extractVariableValues(
      final Map<String, Aggregate> aggregations,
      final DecisionVariableValueRequestDto requestDto,
      final String variableFieldLabel) {
    return extractVariableValues(
        aggregations, requestDto.getResultOffset(), requestDto.getNumResults(), variableFieldLabel);
  }

  private List<String> extractVariableValues(
      final Map<String, Aggregate> aggregations,
      final Integer resultOffset,
      final Integer numResults,
      final String variableFieldLabel) {
    final NestedAggregate variablesFromType = aggregations.get(variableFieldLabel).nested();
    final FilterAggregate filteredVariables =
        variablesFromType.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();
    final List<String> allValues = new ArrayList<>();
    final Aggregate aggregate = filteredVariables.aggregations().get(VALUE_AGGREGATION);

    if (aggregate.isLterms()) {
      final LongTermsAggregate valueTerms =
          filteredVariables.aggregations().get(VALUE_AGGREGATION).lterms();
      for (final LongTermsBucket valueBucket : valueTerms.buckets().array()) {
        allValues.add(
            String.valueOf(
                valueBucket.keyAsString() != null ? valueBucket.keyAsString() : valueBucket.key()));
      }
    } else if (aggregate.isSterms()) {
      final StringTermsAggregate valueTerms =
          filteredVariables.aggregations().get(VALUE_AGGREGATION).sterms();
      for (final StringTermsBucket valueBucket : valueTerms.buckets().array()) {
        allValues.add(valueBucket.key().stringValue());
      }
    } else if (aggregate.isDterms()) {
      final DoubleTermsAggregate valueTerms =
          filteredVariables.aggregations().get(VALUE_AGGREGATION).dterms();
      for (final DoubleTermsBucket valueBucket : valueTerms.buckets().array()) {
        allValues.add(Double.toString(valueBucket.key()));
      }
    }
    return allValues;
  }

  private Map<String, Aggregation> getDecisionVariableValueAggregation(
      final DecisionVariableValueRequestDto requestDto, final String variablePath) {
    final Map<String, Aggregation> filterForVariableWithGivenIdAndPrefix =
        getVariableValueFilterAggregation(
            requestDto.getVariableId(),
            variablePath,
            requestDto.getVariableType(),
            requestDto.getValueFilter());
    return getVariableValuesAggregation(
        variablePath, requestDto.getVariableType(), filterForVariableWithGivenIdAndPrefix);
  }

  private Map<String, Aggregation> getVariableValueAggregation(
      final ProcessVariableValuesQueryDto requestDto) {
    final Map<String, Aggregation> filterForVariableWithGivenIdAndPrefix =
        getProcessVariableValueFilterAggregation(
            requestDto.getName(), requestDto.getType(), requestDto.getValueFilter());
    return getVariableValuesAggregation(
        VARIABLES, requestDto.getType(), filterForVariableWithGivenIdAndPrefix);
  }

  private Map<String, Aggregation> getVariableValuesAggregation(
      final String variablePath,
      final VariableType variableType,
      final Map<String, Aggregation> filterForVariableWithGivenIdAndPrefix) {
    return Map.of(
        variablePath,
        Aggregation.of(
            a ->
                a.nested(n -> n.path(variablePath))
                    .aggregations(filterForVariableWithGivenIdAndPrefix)));
  }

  private Map<String, Aggregation> getVariableValueFilterAggregation(
      final String variableId,
      final String variablePath,
      final VariableType type,
      final String valueFilter) {
    final BoolQuery.Builder filterQuery = new BoolQuery.Builder();
    filterQuery.must(
        m -> m.term(t -> t.field(getVariableClauseIdField(variablePath)).value(variableId)));

    addValueFilter(variablePath, valueFilter, filterQuery);

    return Map.of(
        FILTERED_VARIABLES_AGGREGATION,
        Aggregation.of(
            a ->
                a.filter(Query.of(q -> q.bool(filterQuery.build())))
                    .aggregations(
                        Map.of(
                            VALUE_AGGREGATION,
                            Aggregation.of(
                                a1 ->
                                    a1.terms(
                                        t ->
                                            t.field(
                                                    getVariableValueFieldForType(
                                                        variablePath, type))
                                                .size(MAX_RESPONSE_SIZE_LIMIT)
                                                .order(NamedValue.of("_key", SortOrder.Asc))))))));
  }

  private Map<String, Aggregation> getProcessVariableValueFilterAggregation(
      final String variableName, final VariableType type, final String valueFilter) {
    final BoolQuery.Builder filterQuery =
        new BoolQuery.Builder()
            .must(m -> m.term(t -> t.field(getNestedVariableNameField()).value(variableName)))
            .must(m -> m.term(t -> t.field(getNestedVariableTypeField()).value(type.getId())));
    addValueFilter(type, valueFilter, filterQuery);
    return Map.of(
        FILTERED_VARIABLES_AGGREGATION,
        Aggregation.of(
            a ->
                a.filter(q -> q.bool(filterQuery.build()))
                    .aggregations(
                        Map.of(
                            VALUE_AGGREGATION,
                            Aggregation.of(
                                a1 ->
                                    a1.terms(
                                        t ->
                                            t.field(getVariableValueFieldForType(VARIABLES, type))
                                                .size(MAX_RESPONSE_SIZE_LIMIT)
                                                .order(NamedValue.of("_key", SortOrder.Asc))))))));
  }

  private void addValueFilter(
      final VariableType variableType,
      final String valueFilter,
      final BoolQuery.Builder filterQueryBuilder) {
    final boolean isStringVariable = VariableType.STRING.equals(variableType);
    final boolean valueFilterIsConfigured = valueFilter != null && !valueFilter.isEmpty();
    if (isStringVariable && valueFilterIsConfigured) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      filterQueryBuilder.must(
          m -> {
            if (lowerCaseValue.length() > MAX_GRAM) {
              m.wildcard(
                  w ->
                      w.field(ProcessVariableHelper.getValueSearchField(LOWERCASE_FIELD))
                          .wildcard(buildWildcardQuery(lowerCaseValue)));
            } else {
              m.term(
                  w ->
                      w.field(ProcessVariableHelper.getValueSearchField(N_GRAM_FIELD))
                          .value(lowerCaseValue));
            }
            return m;
          });
    }
  }

  private void addValueFilter(
      final String variablePath, final String valueFilter, final BoolQuery.Builder filterQuery) {
    if (valueFilter != null && !valueFilter.isEmpty()) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      filterQuery.must(
          m -> {
            if (lowerCaseValue.length() > MAX_GRAM) {
              m.wildcard(
                  w ->
                      w.field(getValueSearchField(variablePath, VARIABLE_VALUE_LOWERCASE))
                          .wildcard(buildWildcardQuery(lowerCaseValue)));
            } else {
              m.term(
                  t ->
                      t.field(getValueSearchField(variablePath, VARIABLE_VALUE_NGRAM))
                          .value(lowerCaseValue));
            }
            return m;
          });
    }
  }

  private List<ExternalProcessVariableDto> getPageOfVariablesSortedByIngestionTimestamp(
      final Query query, final int limit) {
    try {
      final SearchResponse<ExternalProcessVariableDto> searchResponse =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
                          .query(query)
                          .sort(
                              ss ->
                                  ss.field(f -> f.field(INGESTION_TIMESTAMP).order(SortOrder.Asc)))
                          .size(limit)),
              ExternalProcessVariableDto.class);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.hits(), ExternalProcessVariableDto.class, objectMapper);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve ingested variables by timestamp!", e);
    }
  }

  private Optional<DefinitionVariableLabelsDto> extractDefinitionLabelsDto(
      final MultiGetResponseItem<DefinitionVariableLabelsDto> multiGetItemResponse) {
    return Optional.ofNullable(multiGetItemResponse.result().source());
  }

  private void addInsertExternalVariableRequest(
      final BulkRequest.Builder bulkRequestBuilder,
      final ExternalProcessVariableDto externalVariable) {
    bulkRequestBuilder.operations(
        o ->
            o.index(
                IndexOperation.of(
                    i ->
                        i.index(
                                esClient
                                    .addPrefixesToIndices(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
                                    .get(0))
                            .document(externalVariable))));
  }
}
