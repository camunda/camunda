/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToValidDefinitionVersion;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToValidVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@AllArgsConstructor
@Slf4j
@Component
public class DefinitionReader {
  private static final String VERSION_AGGREGATION = "versions";
  private static final String VERSION_TAG_AGGREGATION = "versionTags";
  private static final String TENANT_AGGREGATION = "tenants";
  private static final String DEFINITION_TYPE_AGGREGATION = "definitionType";
  private static final String DEFINITION_KEY_AGGREGATION = "definitionKey";
  private static final String NAME_AGGREGATION = "definitionName";
  private static final String[] ALL_DEFINITION_INDEXES =
    {PROCESS_DEFINITION_INDEX_NAME, DECISION_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME};
  private static final String TENANT_NOT_DEFINED_VALUE = "null";

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public Optional<DefinitionWithTenantIdsDto> getDefinition(final DefinitionType type, final String key) {
    if (type == null || key == null) {
      return Optional.empty();
    }

    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DEFINITION_KEY, key));

    return getDefinitionWithTenantIdsDtos(query, resolveIndexNameForType(type)).stream().findFirst();
  }

  public List<DefinitionWithTenantIdsDto> getDefinitionsOfAllTypes() {
    return getDefinitionWithTenantIdsDtos(QueryBuilders.matchAllQuery(), ALL_DEFINITION_INDEXES);
  }

  public List<? extends DefinitionOptimizeDto> getDefinitions(final DefinitionType type,
                                                              final boolean fullyImported,
                                                              final boolean withXml) {
    return fetchDefinitions(type, fullyImported, withXml, matchAllQuery());
  }

  public List<? extends DefinitionOptimizeDto> getFullyImportedDefinitions(final DefinitionType type,
                                                                           final boolean withXml) {
    return getDefinitions(type, true, withXml);
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getDefinitionFromFirstTenantIfAvailable(final DefinitionType type,
                                                                                               final String definitionKey,
                                                                                               final List<String> definitionVersions,
                                                                                               final List<String> tenantIds) {

    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }

    final String mostRecentValidVersion = convertToValidDefinitionVersion(
      definitionVersions, () -> getLatestVersionToKey(type, definitionKey)
    );
    return this.getFullyImportedDefinition(
      type,
      definitionKey,
      mostRecentValidVersion,
      tenantIds.stream()
        // to get a null value if the first element is either absent or null
        .map(Optional::ofNullable).findFirst().flatMap(Function.identity())
        .orElse(null)
    );
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getFullyImportedDefinition(
    final DefinitionType type,
    final String definitionKey,
    final String definitionVersion,
    final String tenantId) {

    if (definitionKey == null || definitionVersion == null) {
      return Optional.empty();
    }

    final String validVersion =
      convertToValidVersion(definitionVersion, () -> getLatestVersionToKey(type, definitionKey));
    final BoolQueryBuilder query = boolQuery()
      .must(termQuery(resolveDefinitionKeyFieldFromType(type), definitionKey))
      .must(termQuery(resolveVersionFieldFromType(type), validVersion))
      .must(existsQuery(resolveXmlFieldFromType(type)));

    if (tenantId != null) {
      query.must(termQuery(TENANT_ID, tenantId));
    } else {
      query.mustNot(existsQuery(TENANT_ID));
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type))
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch [%s] definition with key [%s], version [%s] and tenantId [%s]",
        type,
        definitionKey,
        validVersion,
        tenantId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getTotalHits().value == 0L) {
      log.debug(
        "Could not find [%s] definition with key [{}], version [{}] and tenantId [{}]",
        type,
        definitionKey,
        validVersion,
        tenantId
      );
      return Optional.empty();
    }

    final Class typeClass = resolveDefinitionClassFromType(type);
    final T definitionOptimizeDto = (T) ElasticsearchHelper.mapHits(
      searchResponse.getHits(),
      1,
      typeClass,
      createMappingFunctionForDefinitionType(typeClass)
    ).stream()
      .findFirst()
      .orElse(null);
    return Optional.of(definitionOptimizeDto);
  }

  public Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    // four levels of aggregations
    // 4. group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
      terms(NAME_AGGREGATION)
        .field(DEFINITION_NAME)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        .size(1);
    // 3. group by key
    final TermsAggregationBuilder keyAggregation = terms(DEFINITION_KEY_AGGREGATION)
      .field(DEFINITION_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(nameAggregation);
    // 2. group by _index (type)
    final TermsAggregationBuilder typeAggregation = terms(DEFINITION_TYPE_AGGREGATION)
      .field("_index")
      .subAggregation(keyAggregation);
    // 1. group by tenant
    final TermsAggregationBuilder tenantsAggregation =
      terms(TENANT_AGGREGATION)
        .field(DEFINITION_TENANT_ID)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        // put `null` values (default tenant) into a dedicated bucket
        .missing(TENANT_NOT_DEFINED_VALUE)
        .order(BucketOrder.key(true))
        .subAggregation(typeAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
    searchSourceBuilder.aggregation(tenantsAggregation);
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(ALL_DEFINITION_INDEXES).source(searchSourceBuilder);
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms tenantResult = searchResponse.getAggregations().get(TENANT_AGGREGATION);
      return tenantResult.getBuckets().stream()
        .map(tenantBucket -> {
          // convert not defined bucket back to a `null` id
          final String tenantId = TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantBucket.getKeyAsString())
            ? null
            : tenantBucket.getKeyAsString();
          final Terms typeAggregationResult = tenantBucket.getAggregations().get(DEFINITION_TYPE_AGGREGATION);
          final List<SimpleDefinitionDto> definitionDtos = typeAggregationResult.getBuckets()
            .stream()
            .flatMap(typeBucket -> {
              final DefinitionType definitionType = resolveDefinitionTypeFromIndexAlias(typeBucket.getKeyAsString());
              final Boolean isEventProcess = resolveIsEventProcessFromIndexAlias(typeBucket.getKeyAsString());
              final Terms keyAggregationResult = typeBucket.getAggregations().get(DEFINITION_KEY_AGGREGATION);
              return keyAggregationResult.getBuckets().stream()
                .map(keyBucket -> {
                  final Terms nameResult = keyBucket.getAggregations().get(NAME_AGGREGATION);
                  final String definitionName = nameResult.getBuckets().stream()
                    .findFirst()
                    .map(Terms.Bucket::getKeyAsString)
                    .orElse(null);
                  return new SimpleDefinitionDto(
                    keyBucket.getKeyAsString(),
                    definitionName,
                    definitionType,
                    isEventProcess
                  );
                });
            })
            .collect(toList());
          return new TenantIdWithDefinitionsDto(tenantId, definitionDtos);
        })
        .collect(toMap(TenantIdWithDefinitionsDto::getId, entry -> entry));
    } catch (IOException e) {
      final String reason = "Was not able to fetch definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  public List<DefinitionVersionsWithTenantsDto> getDefinitionsWithVersionsAndTenantsForType(final DefinitionType type,
                                                                                            final boolean excludeEventProcesses) {
    // 6 aggregations over 3 layers:
    // 1. type
    // | 2. key
    // || - 3.1 name
    // || - 3.2 version
    // ||| - 4.1 versionTag
    // ||| - 4.2 tenant
    // 4.2. group by tenant
    final TermsAggregationBuilder tenantsAggregation = terms(TENANT_AGGREGATION)
      .field(DEFINITION_TENANT_ID)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      // put `null` values (default tenant) into a dedicated bucket
      .missing(TENANT_NOT_DEFINED_VALUE)
      .order(BucketOrder.key(true));
    // 4.1. group by versionTag
    final TermsAggregationBuilder versionTagAggregation = terms(VERSION_TAG_AGGREGATION)
      .field(DEFINITION_VERSION_TAG)
      .size(MAX_RESPONSE_SIZE_LIMIT);
    // 3.2. group by version
    final TermsAggregationBuilder versionAggregation = terms(VERSION_AGGREGATION)
      .field(DEFINITION_VERSION)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(versionTagAggregation)
      .subAggregation(tenantsAggregation);
    // 3.1. group by name
    final TermsAggregationBuilder nameAggregation = terms(NAME_AGGREGATION)
      .field(DEFINITION_NAME)
      .size(MAX_RESPONSE_SIZE_LIMIT);
    // 2. group by key
    final TermsAggregationBuilder keyAggregation = terms(DEFINITION_KEY_AGGREGATION)
      .field(DEFINITION_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(nameAggregation)
      .subAggregation(versionAggregation);
    // 1. group by _index (type)
    final TermsAggregationBuilder typeAggregation = terms(DEFINITION_TYPE_AGGREGATION)
      .field("_index")
      .subAggregation(keyAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
    searchSourceBuilder.aggregation(typeAggregation);
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(
      resolveIndexNameForType(type, excludeEventProcesses)
    ).source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms typeResult = searchResponse.getAggregations().get(DEFINITION_TYPE_AGGREGATION);
      return typeResult.getBuckets().stream()
        .flatMap(typeBucket -> {
          final DefinitionType definitionType = resolveDefinitionTypeFromIndexAlias(typeBucket.getKeyAsString());
          final Boolean isEventProcess = resolveIsEventProcessFromIndexAlias(typeBucket.getKeyAsString());
          final Terms keyAggregationResult = typeBucket.getAggregations().get(DEFINITION_KEY_AGGREGATION);
          return keyAggregationResult.getBuckets().stream()
            .map(keyBucket -> createDefinitionVersionsWithTenantsDtosForKeyBucket(
              keyBucket,
              definitionType,
              isEventProcess
            ));
        })
        .collect(toList());
    } catch (IOException e) {
      final String reason = String.format("Was not able to fetch definitions for type [%s].", type);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getDefinitionByKeyAndEngineOmitXml(final DefinitionType type,
                                                                                          final String definitionKey,
                                                                                          final String engineAlias) {

    if (definitionKey == null) {
      return Optional.empty();
    }

    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(resolveDefinitionKeyFieldFromType(type), definitionKey))
      .must(termQuery(ENGINE, engineAlias));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(1)
      .fetchSource(null, resolveXmlFieldFromType(type));
    SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type))
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch %s definition with key [%s], engine [%s]", type, definitionKey, engineAlias
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getTotalHits().value == 0L) {
      return Optional.empty();
    }
    return (Optional<T>) Optional.ofNullable(
      createMappingFunctionForDefinitionType(resolveDefinitionClassFromType(type))
        .apply(searchResponse.getHits().getAt(0)));
  }

  public String getLatestVersionToKey(final DefinitionType type, final String key) {
    log.debug("Fetching latest [{}] definition for key [{}]", type, key);

    Script script = createDefaultScript(
      "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)",
      Collections.emptyMap()
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(resolveDefinitionKeyFieldFromType(type), key))
      .sort(SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
      .size(1);

    SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type))
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch latest [%s] definition for key [%s]",
        type,
        key
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = searchResponse.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(resolveVersionFieldFromType(type))) {
        return sourceAsMap.get(resolveVersionFieldFromType(type)).toString();
      }
    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for process definition key: " + key);
  }

  private List<? extends DefinitionOptimizeDto> fetchDefinitions(final DefinitionType type,
                                                                 final boolean fullyImported,
                                                                 final boolean withXml,
                                                                 final QueryBuilder query) {
    final String xmlField = resolveXmlFieldFromType(type);
    final BoolQueryBuilder rootQuery = boolQuery().must(
      fullyImported ? existsQuery(xmlField) : matchAllQuery()
    );
    rootQuery.must(query);
    final String[] fieldsToExclude = withXml ? null : new String[]{xmlField};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(rootQuery)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(resolveIndexNameForType(type))
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMsg = String.format("Was not able to retrieve definitions of type %s", type);
      log.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }

    final Class typeClass = resolveDefinitionClassFromType(type);
    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      typeClass,
      createMappingFunctionForDefinitionType(typeClass),
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  private DefinitionVersionsWithTenantsDto createDefinitionVersionsWithTenantsDtosForKeyBucket(final Terms.Bucket keyBucket,
                                                                                               final DefinitionType type,
                                                                                               final boolean isEventProcess) {
    final Terms nameResult = keyBucket.getAggregations().get(NAME_AGGREGATION);
    final Optional<? extends Terms.Bucket> nameBucket = nameResult.getBuckets().stream().findFirst();
    final String definitionName = nameBucket.map(MultiBucketsAggregation.Bucket::getKeyAsString).orElse(null);

    final Terms versionsResult = keyBucket.getAggregations().get(VERSION_AGGREGATION);
    final List<DefinitionVersionWithTenantsDto> versions = versionsResult.getBuckets().stream()
      .map(versionBucket -> {
        final String versionTag = versionBucket.getAggregations()
          .<Terms>get(VERSION_TAG_AGGREGATION)
          .getBuckets()
          .stream()
          .findFirst()
          .map(MultiBucketsAggregation.Bucket::getKeyAsString)
          .orElse(null);
        final List<TenantDto> tenants = mapTenantBucketsToTenantDtos(
          versionBucket.getAggregations().get(TENANT_AGGREGATION)
        );

        return new DefinitionVersionWithTenantsDto(
          keyBucket.getKeyAsString(),
          definitionName,
          type,
          isEventProcess,
          versionBucket.getKeyAsString(),
          versionTag,
          tenants
        );
      })
      .sorted(Comparator.comparing(DefinitionVersionWithTenantsDto::getVersion).reversed())
      .collect(toList());
    final List<TenantDto> allTenants = versions.stream()
      .flatMap(v -> v.getTenants().stream())
      .distinct()
      .collect(toList());
    final DefinitionVersionsWithTenantsDto groupedDefinition = new DefinitionVersionsWithTenantsDto(
      keyBucket.getKeyAsString(), definitionName, type, isEventProcess, versions, allTenants
    );
    groupedDefinition.sort();
    return groupedDefinition;
  }

  private List<TenantDto> mapTenantBucketsToTenantDtos(final Terms tenantResult) {
    return tenantResult.getBuckets().stream()
      .map(tenantBucket -> {
        final String tenantId = TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantBucket.getKeyAsString())
          ? null
          : tenantBucket.getKeyAsString();
        return new TenantDto(tenantId, null, null);
      }).collect(toList());
  }

  private List<DefinitionWithTenantIdsDto> getDefinitionWithTenantIdsDtos(final QueryBuilder filterQuery,
                                                                          final String[] definitionIndexNames) {
    // three levels of aggregations
    // 3.1 group by tenant
    final TermsAggregationBuilder tenantsAggregation =
      terms(TENANT_AGGREGATION)
        .field(DEFINITION_TENANT_ID)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        // put `null` values (default tenant) into a dedicated bucket
        .missing(TENANT_NOT_DEFINED_VALUE)
        .order(BucketOrder.key(true));
    // 3.2 group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
      terms(NAME_AGGREGATION)
        .field(DEFINITION_NAME)
        .size(MAX_RESPONSE_SIZE_LIMIT)
        .size(1);
    // 2. group by key
    final TermsAggregationBuilder keyAggregation = terms(DEFINITION_KEY_AGGREGATION)
      .field(DEFINITION_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(tenantsAggregation)
      .subAggregation(nameAggregation);
    // 1. group by _index (type)
    final TermsAggregationBuilder typeAggregation = terms(DEFINITION_TYPE_AGGREGATION)
      .field("_index")
      .subAggregation(keyAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(filterQuery);
    searchSourceBuilder.aggregation(typeAggregation);
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(definitionIndexNames).source(searchSourceBuilder);
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms typeAggregationResult = searchResponse.getAggregations().get(DEFINITION_TYPE_AGGREGATION);
      return typeAggregationResult.getBuckets().stream()
        .flatMap(typeBucket -> {
          final DefinitionType definitionType = resolveDefinitionTypeFromIndexAlias(typeBucket.getKeyAsString());
          final Boolean isEventProcess = resolveIsEventProcessFromIndexAlias(typeBucket.getKeyAsString());
          final Terms keyAggregationResult = typeBucket.getAggregations().get(DEFINITION_KEY_AGGREGATION);
          return keyAggregationResult.getBuckets().stream().map(keyBucket -> {
            final Terms tenantResult = keyBucket.getAggregations().get(TENANT_AGGREGATION);
            final Terms nameResult = keyBucket.getAggregations().get(NAME_AGGREGATION);
            return new DefinitionWithTenantIdsDto(
              keyBucket.getKeyAsString(),
              nameResult.getBuckets().stream().findFirst().map(Terms.Bucket::getKeyAsString).orElse(null),
              definitionType,
              isEventProcess,
              tenantResult.getBuckets().stream()
                .map(Terms.Bucket::getKeyAsString)
                // convert null bucket back to a `null` id
                .map(tenantId -> TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
                .collect(toList())
            );
          });
        })
        .collect(toList());
    } catch (IOException e) {
      final String reason = "Was not able to fetch definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private <T extends DefinitionOptimizeDto> Function<SearchHit, T> createMappingFunctionForDefinitionType(
    final Class<T> type) {
    return hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        T definitionDto = objectMapper.readValue(sourceAsString, type);
        if (ProcessDefinitionOptimizeDto.class.equals(type)) {
          ProcessDefinitionOptimizeDto processDefinition = (ProcessDefinitionOptimizeDto) definitionDto;
          processDefinition.setType(DefinitionType.PROCESS);
          processDefinition.setIsEventBased(resolveIsEventProcessFromIndexAlias(hit.getIndex()));
        } else {
          definitionDto.setType(DefinitionType.DECISION);
        }
        return definitionDto;
      } catch (IOException e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: " + sourceAsString;
        log.error(reason, type.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
  }

  private DefinitionType resolveDefinitionTypeFromIndexAlias(String indexName) {
    if (indexName.equals(getOptimizeIndexNameForIndex(new ProcessDefinitionIndex()))
      || indexName.equals(getOptimizeIndexNameForIndex(new EventProcessDefinitionIndex()))) {
      return DefinitionType.PROCESS;
    } else if (indexName.equals(getOptimizeIndexNameForIndex(new DecisionDefinitionIndex()))) {
      return DefinitionType.DECISION;
    } else {
      throw new OptimizeRuntimeException("Unexpected definition index name: " + indexName);
    }
  }

  private Boolean resolveIsEventProcessFromIndexAlias(String indexName) {
    return indexName.equals(getOptimizeIndexNameForIndex(new EventProcessDefinitionIndex()));
  }

  private String getOptimizeIndexNameForIndex(final StrictIndexMappingCreator index) {
    return esClient.getIndexNameService().getVersionedOptimizeIndexNameForIndexMapping(index);
  }

  private String[] resolveIndexNameForType(final DefinitionType type) {
    return resolveIndexNameForType(type, false);
  }

  private String[] resolveIndexNameForType(final DefinitionType type, final boolean excludeEventProcesses) {
    switch (type) {
      case PROCESS:
        if (excludeEventProcesses) {
          return new String[]{PROCESS_DEFINITION_INDEX_NAME};
        } else {
          return new String[]{PROCESS_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME};
        }
      case DECISION:
        return new String[]{DECISION_DEFINITION_INDEX_NAME};
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private String resolveXmlFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_XML;
      case DECISION:
        return DECISION_DEFINITION_XML;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  private String resolveVersionFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_VERSION;
      case DECISION:
        return DECISION_DEFINITION_VERSION;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  private String resolveDefinitionKeyFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_KEY;
      case DECISION:
        return DECISION_DEFINITION_KEY;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  private <T extends DefinitionOptimizeDto> Class<T> resolveDefinitionClassFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return (Class<T>) ProcessDefinitionOptimizeDto.class;
      case DECISION:
        return (Class<T>) DecisionDefinitionOptimizeDto.class;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }
}
