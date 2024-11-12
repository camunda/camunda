/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lt;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.script;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex.INGESTION_TIMESTAMP;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex.TIMESTAMP;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static io.camunda.optimize.service.util.DefinitionQueryUtilOS.createDefinitionQuery;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
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
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.os.OpenSearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.AggregationDSL;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.reader.OpensearchReaderUtil;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import io.camunda.optimize.service.db.util.ProcessVariableHelper;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionQueryUtilOS;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.CompositeTermsAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.DoubleTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.DoubleTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.mget.MultiGetOperation;
import org.opensearch.client.opensearch.core.mget.MultiGetResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableRepositoryOS implements VariableRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(VariableRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessQueryFilterEnhancerOS processQueryFilterEnhancer;

  public VariableRepositoryOS(
      final OptimizeOpenSearchClient osClient,
      final OptimizeIndexNameService indexNameService,
      final ConfigurationService configurationService,
      final DateTimeFormatter dateTimeFormatter,
      final DecisionDefinitionReader decisionDefinitionReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessQueryFilterEnhancerOS processQueryFilterEnhancer) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
    this.configurationService = configurationService;
    this.dateTimeFormatter = dateTimeFormatter;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.processDefinitionReader = processDefinitionReader;
    this.processQueryFilterEnhancer = processQueryFilterEnhancer;
  }

  @Override
  public void deleteVariableDataByProcessInstanceIds(
      final String processDefinitionKey, final List<String> processInstanceIds) {
    final List<BulkOperation> bulkOperations =
        processInstanceIds.stream()
            .map(
                processInstanceId -> {
                  final UpdateOperation<Void> updateOperation =
                      new UpdateOperation.Builder<Void>()
                          .index(
                              indexNameService.getOptimizeIndexAliasForIndex(
                                  getProcessInstanceIndexAliasName(processDefinitionKey)))
                          .id(processInstanceId)
                          .script(
                              script(
                                  ProcessInstanceScriptFactory.createVariableClearScript(),
                                  Map.of()))
                          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                          .build();
                  return new BulkOperation.Builder().update(updateOperation).build();
                })
            .toList();

    osClient.doBulkRequest(
        () -> new BulkRequest.Builder().refresh(Refresh.True),
        bulkOperations,
        getProcessInstanceIndexAliasName(processDefinitionKey),
        false);
  }

  @Override
  public void upsertVariableLabel(
      final String variableLabelIndexName,
      final DefinitionVariableLabelsDto definitionVariableLabelsDto,
      final ScriptData scriptData) {

    final UpdateRequest.Builder updateRequest =
        new UpdateRequest.Builder()
            .index(variableLabelIndexName)
            .id(definitionVariableLabelsDto.getDefinitionKey().toLowerCase(Locale.ENGLISH))
            .scriptedUpsert(true)
            .script(QueryDSL.script(scriptData.scriptString(), scriptData.params()))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
            .refresh(Refresh.True)
            .upsert(definitionVariableLabelsDto);

    osClient
        .getRichOpenSearchClient()
        .doc()
        .upsert(
            updateRequest,
            DefinitionVariableLabelsDto.class,
            e ->
                String.format(
                    "Was not able to update the variable labels for the process definition with id: [%s]",
                    definitionVariableLabelsDto.getDefinitionKey()));
  }

  @Override
  public void deleteVariablesForDefinition(
      final String variableLabelIndexName, final String processDefinitionKey) {
    osClient.delete(variableLabelIndexName, processDefinitionKey);
  }

  @Override
  public void deleteByProcessInstanceIds(final List<String> processInstanceIds) {
    osClient.deleteByQueryTask(
        String.format("variable updates of %d process instances", processInstanceIds.size()),
        QueryDSL.stringTerms(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds),
        false,
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new VariableUpdateInstanceIndexOS()));
  }

  @Override
  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      final List<String> processDefinitionKeys) {

    final List<MultiGetOperation> operations =
        processDefinitionKeys.stream()
            .filter(Objects::nonNull)
            .map(
                key ->
                    new MultiGetOperation.Builder()
                        .id(key.toLowerCase(Locale.ENGLISH))
                        .index(
                            osClient
                                .getIndexNameService()
                                .getOptimizeIndexAliasForIndex(VARIABLE_LABEL_INDEX_NAME))
                        .build())
            .toList();

    final String errorMessage =
        String.format(
            "There was an error while fetching documents from the variable label index with keys %s.",
            processDefinitionKeys);
    final MgetResponse<DefinitionVariableLabelsDto> response =
        osClient.mget(DefinitionVariableLabelsDto.class, errorMessage, operations);

    return response.docs().stream()
        .map(MultiGetResponseItem::result)
        .filter(GetResult::found)
        .map(GetResult::source)
        .filter(Objects::nonNull)
        .peek(label -> label.setDefinitionKey(label.getDefinitionKey().toLowerCase(Locale.ENGLISH)))
        .collect(
            Collectors.toMap(DefinitionVariableLabelsDto::getDefinitionKey, Function.identity()));
  }

  @Override
  public List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(
      final Set<String> processInstanceIds) {

    final Query query =
        QueryDSL.stringTerms(VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    final SearchRequest.Builder searchRequest =
        new Builder()
            .index(DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
            .query(query)
            .sort(
                new SortOptions.Builder()
                    .field(new FieldSort.Builder().field(TIMESTAMP).order(SortOrder.Asc).build())
                    .build())
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .scroll(
                new Time.Builder()
                    .time(
                        String.valueOf(
                            configurationService
                                .getOpenSearchConfiguration()
                                .getScrollTimeoutInSeconds()))
                    .build());
    final OpenSearchDocumentOperations.AggregatedResult<Hit<VariableUpdateInstanceDto>> scrollResp;
    try {
      scrollResp =
          osClient.retrieveAllScrollResults(searchRequest, VariableUpdateInstanceDto.class);
    } catch (final IOException e) {
      LOG.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }

  @Override
  public void writeExternalProcessVariables(
      final List<ExternalProcessVariableDto> variables, final String itemName) {
    final List<BulkOperation> bulkOperations =
        variables.stream().map(this::createInsertExternalVariableOperation).toList();

    osClient.doBulkRequest(
        BulkRequest.Builder::new,
        bulkOperations,
        indexNameService.getOptimizeIndexAliasForIndex(
            DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME),
        false);
  }

  @Override
  public void deleteExternalVariablesIngestedBefore(
      final OffsetDateTime evaluationDate, final String deletedItemIdentifier) {

    osClient.deleteByQueryTask(
        deletedItemIdentifier,
        lt(
            ExternalProcessVariableDto.Fields.ingestionTimestamp,
            dateTimeFormatter.format(evaluationDate)),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new ExternalProcessVariableIndexOS()));
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(
      final Long ingestTimestamp, final int limit) {
    return getPageOfVariablesSortedByIngestionTimestamp(
        QueryDSL.gt(INGESTION_TIMESTAMP, ingestTimestamp), limit);
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(final Long ingestTimestamp) {
    return getPageOfVariablesSortedByIngestionTimestamp(
        QueryDSL.gteLte(INGESTION_TIMESTAMP, ingestTimestamp, ingestTimestamp),
        MAX_RESPONSE_SIZE_LIMIT);
  }

  @Override
  public List<String> getDecisionVariableValues(
      final DecisionVariableValueRequestDto requestDto, final String variablesPath) {
    final Query query =
        createDefinitionQuery(
            requestDto.getDecisionDefinitionKey(),
            requestDto.getDecisionDefinitionVersions(),
            requestDto.getTenantIds(),
            new DecisionInstanceIndexOS(requestDto.getDecisionDefinitionKey()),
            decisionDefinitionReader::getLatestVersionToKey);

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey()))
            .query(query)
            .aggregations(getDecisionVariableValueAggregation(requestDto, variablesPath));

    try {
      final String errorMsg =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getVariableId(), requestDto.getVariableType());
      final SearchResponse<DecisionInstanceDto> searchResponse =
          osClient.search(searchRequest, DecisionInstanceDto.class, errorMsg);
      final Map<String, Aggregate> aggregations = searchResponse.aggregations();
      return extractVariableValues(aggregations, requestDto, variablesPath);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(DECISION, e)) {
        LOG.info(
            "Was not able to fetch variable values because no instance index with alias {} exists. "
                + "Returning empty list.",
            getDecisionInstanceIndexAliasName(requestDto.getDecisionDefinitionKey()));
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private List<String> extractVariableValues(
      final Map<String, Aggregate> aggregations,
      final DecisionVariableValueRequestDto requestDto,
      final String variableFieldLabel) {
    return extractVariableValues(
        aggregations, requestDto.getResultOffset(), requestDto.getNumResults(), variableFieldLabel);
  }

  private List<String> extractVariableValues(
      final Map<String, Aggregate> aggregations, final ProcessVariableValuesQueryDto requestDto) {
    return extractVariableValues(
        aggregations, requestDto.getResultOffset(), requestDto.getNumResults(), VARIABLES);
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableNameRequest,
      final List<ProcessToQueryDto> validNameRequests,
      final List<String> processDefinitionKeys,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    final List<Query> definitionQueries =
        validNameRequests.stream()
            .map(
                request ->
                    createDefinitionQuery(
                        request.getProcessDefinitionKey(),
                        request.getProcessDefinitionVersions(),
                        request.getTenantIds(),
                        new ProcessInstanceIndexES(request.getProcessDefinitionKey()),
                        processDefinitionReader::getLatestVersionToKey))
            .toList();
    final List<ProcessFilterDto<?>> processFilterDtos =
        variableNameRequest.getFilter().stream()
            .filter(filter -> filter.getAppliedTo().contains(APPLIED_TO_ALL_DEFINITIONS))
            .toList();
    final FilterContext filterContext =
        FilterContext.builder().timezone(variableNameRequest.getTimezone()).build();
    final List<Query> filterQueries =
        processQueryFilterEnhancer.filterQueries(processFilterDtos, filterContext);
    final BoolQuery.Builder query =
        new BoolQuery.Builder()
            .minimumShouldMatch("1")
            .should(definitionQueries)
            .filter(filterQueries);
    return getVariableNamesForInstancesMatchingQuery(
        processDefinitionKeys, query, definitionLabelsDtos);
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final Supplier<co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder>
          baseQueryBuilderSupplier,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    LOG.debug(
        "getVariableNamesForInstancesMatchingQuery: Functionality not implemented for OpenSearch");
    return List.of();
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final BoolQuery.Builder baseQuery,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    final List<Map<String, CompositeAggregationSource>> variableNameAndTypeTerms =
        new ArrayList<>();
    variableNameAndTypeTerms.add(
        Collections.singletonMap(
            NAME_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(
                    new CompositeTermsAggregationSource.Builder()
                        .field(getNestedVariableNameField())
                        .build())
                .build()));
    variableNameAndTypeTerms.add(
        Collections.singletonMap(
            TYPE_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(
                    new CompositeTermsAggregationSource.Builder()
                        .field(getNestedVariableTypeField())
                        .build())
                .build()));
    variableNameAndTypeTerms.add(
        Collections.singletonMap(
            INDEX_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(
                    new CompositeTermsAggregationSource.Builder().field(INDEX_AGGREGATION).build())
                .build()));

    final CompositeAggregation varNameAndTypeAgg =
        new CompositeAggregation.Builder()
            .sources(variableNameAndTypeTerms)
            .size(configurationService.getOpenSearchConfiguration().getAggregationBucketLimit())
            .build();

    final NestedAggregation nestedAgg = new NestedAggregation.Builder().path(VARIABLES).build();

    final Aggregation userTasksAgg =
        AggregationDSL.withSubaggregations(
            nestedAgg,
            Collections.singletonMap(
                VAR_NAME_AND_TYPE_COMPOSITE_AGG, varNameAndTypeAgg._toAggregation()));

    final List<String> indicesToTarget =
        processDefinitionKeysToTarget.stream()
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toList();

    final List<ProcessVariableNameResponseDto> variableNames = new ArrayList<>();
    final OpenSearchCompositeAggregationScroller compositeAggregationScroller =
        OpenSearchCompositeAggregationScroller.create()
            .setClient(osClient)
            .query(baseQuery.build().toQuery())
            .aggregations(Map.of(VARIABLES, userTasksAgg))
            .index(indicesToTarget)
            .size(0)
            .setPathToAggregation(VARIABLES, VAR_NAME_AND_TYPE_COMPOSITE_AGG)
            .setCompositeBucketConsumer(
                bucket ->
                    variableNames.add(extractVariableNameAndLabel(bucket, definitionLabelsDtos)));
    compositeAggregationScroller.consumeAllPages();

    return filterVariableNameResults(variableNames);
  }

  private ProcessVariableNameResponseDto extractVariableNameAndLabel(
      final CompositeBucket bucket,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsByKey) {
    final String processDefinitionKey =
        extractProcessDefinitionKeyFromIndexName(
            (bucket.key().get(INDEX_AGGREGATION).to(String.class)));
    final String variableName = bucket.key().get(NAME_AGGREGATION).to(String.class);
    final String variableType = bucket.key().get(TYPE_AGGREGATION).to(String.class);
    return processVariableNameResponseDtoFrom(
        definitionLabelsByKey, processDefinitionKey, variableName, variableType);
  }

  @Override
  public List<String> getVariableValues(
      final ProcessVariableValuesQueryDto requestDto,
      final List<ProcessVariableSourceDto> processVariableSources) {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    processVariableSources.forEach(
        source -> {
          query.should(
              DefinitionQueryUtilOS.createDefinitionQuery(
                  source.getProcessDefinitionKey(),
                  source.getProcessDefinitionVersions(),
                  source.getTenantIds(),
                  new ProcessInstanceIndexOS(source.getProcessDefinitionKey()),
                  processDefinitionReader::getLatestVersionToKey));
          if (source.getProcessInstanceId() != null) {
            query.must(
                QueryDSL.term(
                    ProcessInstanceIndex.PROCESS_INSTANCE_ID, source.getProcessInstanceId()));
          }
        });

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .query(query.build().toQuery())
            .size(0)
            .aggregations(getVariableValueAggregation(requestDto))
            .index(PROCESS_INSTANCE_MULTI_ALIAS);
    try {
      final String errorMsg =
          String.format(
              "Was not able to fetch values for variable [%s] with type [%s] ",
              requestDto.getName(), requestDto.getType().getId());
      final SearchResponse<ProcessInstanceDto> searchResponse =
          osClient.search(searchRequest, ProcessInstanceDto.class, errorMsg);
      final Map<String, Aggregate> aggregations = searchResponse.aggregations();

      return extractVariableValues(aggregations, requestDto);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to fetch variable values because no instance indices exist. Returning empty list.");
        return Collections.emptyList();
      }
      throw e;
    }
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
    if (filteredVariables.aggregations().get(VALUE_AGGREGATION).isSterms()) {
      final StringTermsAggregate valueTerms =
          filteredVariables.aggregations().get(VALUE_AGGREGATION).sterms();
      for (final StringTermsBucket valueBucket : valueTerms.buckets().array()) {
        allValues.add(valueBucket.key());
      }
    } else if (filteredVariables.aggregations().get(VALUE_AGGREGATION).isDterms()) {
      final DoubleTermsAggregate valueTerms =
          filteredVariables.aggregations().get(VALUE_AGGREGATION).dterms();
      for (final DoubleTermsBucket valueBucket : valueTerms.buckets().array()) {
        allValues.add(String.valueOf(valueBucket.key()));
      }
    } else if (filteredVariables.aggregations().get(VALUE_AGGREGATION).isLterms()) {
      final LongTermsAggregate valueTerms =
          filteredVariables.aggregations().get(VALUE_AGGREGATION).lterms();
      for (final LongTermsBucket valueBucket : valueTerms.buckets().array()) {
        // This is necessary because if this is e.g. a date, the keyAsString() will return the
        // timestamp, if it is just a number, keyAsString will be null
        if (valueBucket.keyAsString() != null) {
          allValues.add(valueBucket.keyAsString());
        } else {
          allValues.add(String.valueOf(valueBucket.key()));
        }
      }
    }

    final int lastIndex = Math.min(allValues.size(), resultOffset + numResults);
    return allValues.subList(resultOffset, lastIndex);
  }

  private Map<String, Aggregation> getVariableValuesAggregation(
      final String variablePath,
      final VariableType variableType,
      final ContainerBuilder filterForVariableWithGivenIdAndPrefix) {
    final TermsAggregation collectAllVariableValues =
        new TermsAggregation.Builder()
            .field(getVariableValueFieldForType(variablePath, variableType))
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .order(Map.of("_key", SortOrder.Asc))
            .build();

    filterForVariableWithGivenIdAndPrefix.aggregations(
        Map.of(VALUE_AGGREGATION, collectAllVariableValues._toAggregation()));

    final NestedAggregation.Builder nestedAgg = new NestedAggregation.Builder().path(variablePath);
    final Aggregation finalAgg =
        new Aggregation.Builder()
            .nested(nestedAgg.build())
            .aggregations(
                Map.of(
                    FILTERED_VARIABLES_AGGREGATION, filterForVariableWithGivenIdAndPrefix.build()))
            .build();

    return Map.of(variablePath, finalAgg);
  }

  private Map<String, Aggregation> getDecisionVariableValueAggregation(
      final DecisionVariableValueRequestDto requestDto, final String variablePath) {
    final ContainerBuilder filterForVariableWithGivenIdAndPrefix =
        getVariableValueFilterAggregation(
            requestDto.getVariableId(), variablePath, requestDto.getValueFilter());
    return getVariableValuesAggregation(
        variablePath, requestDto.getVariableType(), filterForVariableWithGivenIdAndPrefix);
  }

  private Map<String, Aggregation> getVariableValueAggregation(
      final ProcessVariableValuesQueryDto requestDto) {
    final ContainerBuilder filterForVariableWithGivenIdAndPrefix =
        getVariableValueFilterAggregation(
            requestDto.getName(), requestDto.getType(), requestDto.getValueFilter());
    return getVariableValuesAggregation(
        VARIABLES, requestDto.getType(), filterForVariableWithGivenIdAndPrefix);
  }

  private ContainerBuilder getVariableValueFilterAggregation(
      final String variableId, final String variablePath, final String valueFilter) {
    Query filterQuery = QueryDSL.term(getVariableClauseIdField(variablePath), variableId);
    filterQuery = addValueFilter(variablePath, valueFilter, filterQuery);
    return new Aggregation.Builder().filter(filterQuery);
  }

  private ContainerBuilder getVariableValueFilterAggregation(
      final String variableName, final VariableType type, final String valueFilter) {
    final Query filterQuery1 = QueryDSL.term(getNestedVariableNameField(), variableName);
    final Query filterQuery2 = QueryDSL.term(getNestedVariableTypeField(), type.getId());
    final Query filterQuery =
        addValueFilter(type, valueFilter, QueryDSL.and(filterQuery1, filterQuery2));
    return new Aggregation.Builder().filter(filterQuery);
  }

  private Query addValueFilter(
      final VariableType variableType, final String valueFilter, final Query filterQuery) {
    final boolean isStringVariable = VariableType.STRING.equals(variableType);
    final boolean valueFilterIsConfigured = valueFilter != null && !valueFilter.isEmpty();
    if (isStringVariable && valueFilterIsConfigured) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      final Query filter =
          // using the slow wildcard query for uncommonly large filter strings (>10 chars)
          (lowerCaseValue.length() > MAX_GRAM)
              ? QueryDSL.wildcardQuery(
                  ProcessVariableHelper.getValueSearchField(LOWERCASE_FIELD),
                  buildWildcardQuery(lowerCaseValue))
              /*
                using ngrams to filter for strings < 10 chars,
                because it's fast but increasing the number of chars makes the index bigger
              */
              : QueryDSL.term(
                  ProcessVariableHelper.getValueSearchField(N_GRAM_FIELD), lowerCaseValue);

      return QueryDSL.and(filterQuery, filter);
    }
    return filterQuery;
  }

  private Query addValueFilter(
      final String variablePath, final String valueFilter, final Query filterQuery) {
    if (valueFilter != null && !valueFilter.isEmpty()) {
      final String lowerCaseValue = valueFilter.toLowerCase(Locale.ENGLISH);
      final Query filter =
          // using the slow wildcard query for uncommonly large filter strings (>10 chars)
          (lowerCaseValue.length() > MAX_GRAM)
              ? QueryDSL.wildcardQuery(
                  getValueSearchField(variablePath, VARIABLE_VALUE_LOWERCASE),
                  buildWildcardQuery(lowerCaseValue))
              /*
                using ngrams to filter for strings < 10 chars,
                because it's fast but increasing the number of chars makes the index bigger
              */
              : QueryDSL.term(
                  getValueSearchField(variablePath, VARIABLE_VALUE_NGRAM), lowerCaseValue);

      return QueryDSL.and(filterQuery, filter);
    }
    return filterQuery;
  }

  private List<ExternalProcessVariableDto> getPageOfVariablesSortedByIngestionTimestamp(
      final Query query, final int limit) {
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .query(query)
            .index(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(INGESTION_TIMESTAMP)
                            .order(SortOrder.Asc)
                            .build())
                    .build())
            .size(limit);

    final SearchResponse<ExternalProcessVariableDto> searchResponse =
        osClient.search(
            searchRequest,
            ExternalProcessVariableDto.class,
            "Was not able to retrieve ingested variables by timestamp!");
    return searchResponse.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
  }

  private BulkOperation createInsertExternalVariableOperation(
      final ExternalProcessVariableDto externalVariable) {
    final IndexOperation<ExternalProcessVariableDto> indexOp =
        new IndexOperation.Builder<ExternalProcessVariableDto>()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(
                    EXTERNAL_PROCESS_VARIABLE_INDEX_NAME))
            .document(externalVariable)
            .build();
    return new BulkOperation.Builder().index(indexOp).build();
  }
}
