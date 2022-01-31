/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.service.es.CompositeAggregationScroller;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedTopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.TENANT_ID;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToLatestParticularVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@AllArgsConstructor
@Slf4j
@Component
public class DefinitionReader {
  private static final String VERSION_AGGREGATION = "versions";
  private static final String VERSION_TAG_AGGREGATION = "versionTags";
  private static final String TENANT_AGGREGATION = "tenants";
  private static final String ENGINE_AGGREGATION = "engines";
  private static final String TOP_HITS_AGGREGATION = "topHits";
  private static final String DEFINITION_KEY_FILTER_AGGREGATION = "definitionKeyFilter";
  private static final String DEFINITION_TYPE_AGGREGATION = "definitionType";
  private static final String DEFINITION_KEY_AGGREGATION = "definitionKey";
  private static final String DEFINITION_KEY_AND_TYPE_AGGREGATION = "definitionKeyAndType";
  private static final String DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION = "definitionKeyAndTypeAndTenant";
  private static final String NAME_AGGREGATION = "definitionName";
  private static final String[] ALL_DEFINITION_INDEXES =
    {PROCESS_DEFINITION_INDEX_NAME, DECISION_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME};
  private static final String TENANT_NOT_DEFINED_VALUE = "null";

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(final DefinitionType type,
                                                                                final String key) {
    if (type == null || key == null) {
      return Optional.empty();
    }
    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DEFINITION_KEY, key))
      .must(termQuery(DEFINITION_DELETED, false));

    return getDefinitionWithTenantIdsDtos(query, resolveIndexNameForType(type)).stream().findFirst();
  }

  public List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(final DefinitionType type,
                                                                                   final Set<String> keys,
                                                                                   final Set<String> tenantIds) {
    final BoolQueryBuilder filterQuery = boolQuery();
    filterQuery.filter(
      boolQuery().minimumShouldMatch(1)
        .must(termQuery(DEFINITION_DELETED, false))
        // use separate should queries as definition type may be null (returning both process and decision)
        .should(existsQuery(PROCESS_DEFINITION_XML))
        .should(existsQuery(DECISION_DEFINITION_XML))
    );

    if (!CollectionUtils.isEmpty(keys)) {
      filterQuery.filter(termsQuery(DEFINITION_KEY, keys));
    }

    addTenantIdFilter(tenantIds, filterQuery);

    return getDefinitionWithTenantIdsDtos(filterQuery, resolveIndexNameForType(type));
  }

  public <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedDefinitions(final DefinitionType type,
                                                                                       final boolean withXml) {
    return getDefinitions(type, true, withXml, false);
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getFirstFullyImportedDefinitionFromTenantsIfAvailable(
    final DefinitionType type,
    final String definitionKey,
    final List<String> definitionVersions,
    final List<String> tenantIds) {
    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }

    final String mostRecentValidVersion = DefinitionVersionHandlingUtil.convertToLatestParticularVersion(
      definitionVersions, () -> getLatestVersionToKey(type, definitionKey)
    );

    Optional<T> definition = Optional.empty();
    for (String tenantId : tenantIds) {
      definition = getFullyImportedDefinition(
        type,
        definitionKey,
        mostRecentValidVersion,
        tenantId
      );
      if (definition.isPresent()) {
        // return the first found definition
        break;
      }
    }
    return definition;
  }

  public <T extends DefinitionOptimizeResponseDto> List<T> getLatestFullyImportedDefinitionsFromTenantsIfAvailable(
    final DefinitionType type,
    final String definitionKey) {
    if (definitionKey == null) {
      return Collections.emptyList();
    }

    return getLatestFullyImportedDefinitionPerTenant(type, definitionKey);
  }

  public Set<String> getDefinitionEngines(final DefinitionType type, final String definitionKey) {
    final TermsAggregationBuilder enginesAggregation =
      terms(ENGINE_AGGREGATION)
        .field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
        .size(LIST_FETCH_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery()
               .must(termQuery(resolveDefinitionKeyFieldFromType(type), definitionKey))
               .must(termQuery(DEFINITION_DELETED, false)))
      // no search results needed, we only need the aggregation
      .size(0)
      .aggregation(enginesAggregation);

    final SearchRequest searchRequest = new SearchRequest(
      DefinitionType.PROCESS.equals(type) ? PROCESS_DEFINITION_INDEX_NAME : DECISION_DEFINITION_INDEX_NAME
    ).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch engines for definition key [%s] and type [%s]", definitionKey, type
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getAggregations().<Terms>get(ENGINE_AGGREGATION).getBuckets().stream()
      .map(MultiBucketsAggregation.Bucket::getKeyAsString)
      .collect(Collectors.toSet());
  }

  public Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    // 2.2 group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
      terms(NAME_AGGREGATION)
        .field(DEFINITION_NAME)
        .size(1);
    // 2.1 group by engine
    final TermsAggregationBuilder enginesAggregation =
      terms(ENGINE_AGGREGATION)
        .field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
        .size(LIST_FETCH_LIMIT);
    // 1. group by key, type and tenant (composite aggregation)
    List<CompositeValuesSourceBuilder<?>> keyAndTypeAndTenantSources = new ArrayList<>();
    keyAndTypeAndTenantSources.add(
      new TermsValuesSourceBuilder(TENANT_AGGREGATION).field(DEFINITION_TENANT_ID)
        .missingBucket(true)
        .order(SortOrder.ASC)
    );
    keyAndTypeAndTenantSources.add(new TermsValuesSourceBuilder(DEFINITION_KEY_AGGREGATION).field(DEFINITION_KEY));
    keyAndTypeAndTenantSources
      .add(new TermsValuesSourceBuilder(DEFINITION_TYPE_AGGREGATION).field(ElasticsearchConstants.INDEX));

    CompositeAggregationBuilder keyAndTypeAndTenantAggregation =
      new CompositeAggregationBuilder(DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION, keyAndTypeAndTenantSources)
        .size(configurationService.getEsAggregationBucketLimit())
        .subAggregation(nameAggregation)
        .subAggregation(enginesAggregation);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(DEFINITION_DELETED, false))
      .aggregation(keyAndTypeAndTenantAggregation)
      .size(0);

    SearchRequest searchRequest = new SearchRequest(ALL_DEFINITION_INDEXES)
      .source(searchSourceBuilder);

    final Map<String, List<ParsedComposite.ParsedBucket>> keyAndTypeAggBucketsByTenantId = new HashMap<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION)
      .setCompositeBucketConsumer(
        bucket -> {
          final String tenantId = (String) (bucket.getKey()).get(TENANT_AGGREGATION);
          if (!keyAndTypeAggBucketsByTenantId.containsKey(tenantId)) {
            keyAndTypeAggBucketsByTenantId.put(tenantId, new ArrayList<>());
          }
          keyAndTypeAggBucketsByTenantId.get(tenantId).add(bucket);
        })
      .consumeAllPages();

    Map<String, TenantIdWithDefinitionsDto> resultMap = new HashMap<>();
    keyAndTypeAggBucketsByTenantId.forEach((key, value) -> {
      // convert not defined bucket back to a `null` id
      final String tenantId = TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(key)
        ? null
        : key;
      List<SimpleDefinitionDto> simpleDefinitionDtos = value.stream().map(parsedBucket -> {
        final String indexAliasName = (String) (parsedBucket.getKey().get(DEFINITION_TYPE_AGGREGATION));
        final String definitionKey = (String) (parsedBucket.getKey().get(DEFINITION_KEY_AGGREGATION));
        final String definitionName = ((Terms) parsedBucket.getAggregations().get(NAME_AGGREGATION))
          .getBuckets()
          .stream()
          .findFirst()
          .map(Terms.Bucket::getKeyAsString)
          .orElse(null);
        final Terms enginesResult = parsedBucket.getAggregations().get(ENGINE_AGGREGATION);
        return new SimpleDefinitionDto(
          definitionKey,
          definitionName,
          resolveDefinitionTypeFromIndexAlias(indexAliasName),
          resolveIsEventProcessFromIndexAlias(indexAliasName),
          enginesResult.getBuckets().stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toSet())
        );
      })
        .collect(toList());
      resultMap.put(tenantId, new TenantIdWithDefinitionsDto(tenantId, simpleDefinitionDtos));
    });

    return resultMap;
  }

  public String getLatestVersionToKey(final DefinitionType type, final String key) {
    log.debug("Fetching latest [{}] definition for key [{}]", type, key);

    Script script = createDefaultScript(
      "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)"
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery()
               .must(termQuery(resolveDefinitionKeyFieldFromType(type), key))
               .must(termQuery(DEFINITION_DELETED, false)))
      .sort(SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
      .size(1);

    SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type))
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
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
    throw new OptimizeRuntimeException("Unable to retrieve latest version for " + type + " definition key: " + key);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(final DefinitionType type,
                                                                  final String key,
                                                                  final Set<String> tenantIds) {
    final BoolQueryBuilder filterQuery = boolQuery()
      .filter(termQuery(DEFINITION_KEY, key)).filter(termQuery(DEFINITION_DELETED, false));
    addTenantIdFilter(tenantIds, filterQuery);

    final TermsAggregationBuilder versionTagAggregation = terms(VERSION_TAG_AGGREGATION)
      .field(DEFINITION_VERSION_TAG)
      // there should be only one tag, and for duplicate entries we accept that just one wins
      .size(1);
    final TermsAggregationBuilder versionAggregation = terms(VERSION_AGGREGATION)
      .field(DEFINITION_VERSION)
      .size(configurationService.getEsAggregationBucketLimit())
      .subAggregation(versionTagAggregation);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .aggregation(versionAggregation)
      .size(0);
    final SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type))
      .source(searchSourceBuilder);
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch [%s] definition versions with key [%s], tenantIds [%s]", type, key, tenantIds
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getAggregations().<Terms>get(VERSION_AGGREGATION).getBuckets().stream()
      .map(versionBucket -> {
        final String version = versionBucket.getKeyAsString();
        final Terms versionTags = versionBucket.getAggregations().get(VERSION_TAG_AGGREGATION);
        final String versionTag = versionTags.getBuckets()
          .stream()
          .findFirst()
          .map(MultiBucketsAggregation.Bucket::getKeyAsString)
          .orElse(null);
        return new DefinitionVersionResponseDto(version, versionTag);
      })
      .sorted(Comparator.comparing(DefinitionVersionResponseDto::getVersion).reversed())
      .collect(Collectors.toList());
  }

  public List<String> getDefinitionTenantIds(final DefinitionType type,
                                             final String key,
                                             final List<String> versions,
                                             final Supplier<String> latestVersionSupplier) {
    final BoolQueryBuilder filterQuery = boolQuery()
      .filter(termQuery(DEFINITION_KEY, key))
      .filter(termQuery(DEFINITION_DELETED, false));

    if (!CollectionUtils.isEmpty(versions) &&
      // if all is among the versions, no filtering needed
      !DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(versions)) {
      filterQuery.filter(termsQuery(
        DEFINITION_VERSION,
        versions.stream()
          .filter(version -> !ALL_VERSIONS.equalsIgnoreCase(version))
          .map(version -> convertToLatestParticularVersion(version, latestVersionSupplier))
          .collect(Collectors.toSet())
      ));
    }

    final TermsAggregationBuilder versionAggregation = terms(TENANT_AGGREGATION)
      .field(DEFINITION_TENANT_ID)
      // put `null` values (default tenant) into a dedicated bucket
      .missing(TENANT_NOT_DEFINED_VALUE)
      .size(configurationService.getEsAggregationBucketLimit());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .aggregation(versionAggregation)
      .size(0);
    final SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type))
      .source(searchSourceBuilder);
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch [%s] definition tenants with key [%s] and versions [%s].", type, key, versions
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getAggregations().<Terms>get(TENANT_AGGREGATION).getBuckets().stream()
      .map(Terms.Bucket::getKeyAsString)
      // convert null bucket back to a `null` id
      .map(tenantId -> TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
      .collect(Collectors.toList());
  }

  protected <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                             final boolean fullyImported,
                                                                             final boolean withXml,
                                                                             final boolean includeDeleted) {
    return getDefinitions(type, Collections.emptySet(), fullyImported, withXml, includeDeleted);
  }

  protected <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                             final Set<String> definitionKeys,
                                                                             final boolean fullyImported,
                                                                             final boolean withXml,
                                                                             final boolean includeDeleted) {
    final String xmlField = resolveXmlFieldFromType(type);
    final BoolQueryBuilder rootQuery = boolQuery().must(
      fullyImported ? existsQuery(xmlField) : matchAllQuery()
    );
    final BoolQueryBuilder filteredQuery = rootQuery.must(matchAllQuery());
    if (!includeDeleted) {
      filteredQuery.must(termQuery(DEFINITION_DELETED, false));
    }
    if (!definitionKeys.isEmpty()) {
      filteredQuery.must(termsQuery(resolveDefinitionKeyFieldFromType(type), definitionKeys.toArray()));
    }
    return getDefinitions(type, filteredQuery, withXml);
  }

  protected <T extends DefinitionOptimizeResponseDto> List<T> getDefinitionsByIds(final DefinitionType type,
                                                                                  final Set<String> definitionIds,
                                                                                  final boolean fullyImported,
                                                                                  final boolean withXml,
                                                                                  final boolean includeDeleted) {

    final String xmlField = resolveXmlFieldFromType(type);
    final BoolQueryBuilder rootQuery = boolQuery().must(
      fullyImported ? existsQuery(xmlField) : matchAllQuery()
    );
    final BoolQueryBuilder filteredQuery = rootQuery.must(matchAllQuery());
    if (!includeDeleted) {
      filteredQuery.must(termQuery(DEFINITION_DELETED, false));
    }
    if (!definitionIds.isEmpty()) {
      filteredQuery.must(termsQuery(resolveDefinitionIdFieldFromType(type), definitionIds.toArray()));
    }
    return getDefinitions(type, filteredQuery, withXml);
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(final DefinitionType type,
                                                                           final BoolQueryBuilder filterQuery,
                                                                           final boolean withXml) {
    final String xmlField = resolveXmlFieldFromType(type);
    final String[] fieldsToExclude = withXml ? null : new String[]{xmlField};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(resolveIndexNameForType(type))
        .source(searchSourceBuilder)
        .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      final String errorMsg = String.format("Was not able to retrieve definitions of type %s", type);
      log.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      typeClass,
      createMappingFunctionForDefinitionType(typeClass),
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  private <T extends DefinitionOptimizeResponseDto> Optional<T> getFullyImportedDefinition(
    final DefinitionType type,
    final String definitionKey,
    final String definitionVersion,
    final String tenantId) {

    if (definitionKey == null || definitionVersion == null) {
      return Optional.empty();
    }

    final String validVersion =
      convertToLatestParticularVersion(definitionVersion, () -> getLatestVersionToKey(type, definitionKey));
    final BoolQueryBuilder query = boolQuery()
      .must(termQuery(resolveDefinitionKeyFieldFromType(type), definitionKey))
      .must(termQuery(resolveVersionFieldFromType(type), validVersion))
      .must(termQuery(DEFINITION_DELETED, false))
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
      searchResponse = esClient.search(searchRequest);
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
        "Could not find [{}] definition with key [{}], version [{}] and tenantId [{}]",
        type, definitionKey, validVersion, tenantId
      );
      return Optional.empty();
    }

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final T definitionOptimizeDto = ElasticsearchReaderUtil.mapHits(
      searchResponse.getHits(),
      1,
      typeClass,
      createMappingFunctionForDefinitionType(typeClass)
    ).stream()
      .findFirst()
      .orElse(null);
    return Optional.ofNullable(definitionOptimizeDto);
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> getLatestFullyImportedDefinitionPerTenant(final DefinitionType type,
                                                                                                      final String key) {
    log.debug("Fetching latest fully imported [{}] definitions for key [{}] on each tenant", type, key);

    final FilterAggregationBuilder keyFilterAgg =
      filter(
        DEFINITION_KEY_FILTER_AGGREGATION,
        boolQuery()
          .must(termQuery(resolveDefinitionKeyFieldFromType(type), key))
          .must(termQuery(DEFINITION_DELETED, false))
          .must(existsQuery(resolveXmlFieldFromType(type)))
      );

    final TermsAggregationBuilder tenantsAggregation =
      terms(TENANT_AGGREGATION)
        .field(DEFINITION_TENANT_ID)
        .size(LIST_FETCH_LIMIT)
        .missing(TENANT_NOT_DEFINED_VALUE);

    final Script numericVersionScript = createDefaultScript(
      "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)"
    );

    final TermsAggregationBuilder versionAggregation =
      terms(VERSION_AGGREGATION)
        .field(DEFINITION_VERSION)
        .size(1) // only return bucket for latest version
        .order(BucketOrder.aggregation("versionForSorting", false))
        // custom sort agg to sort by numeric version value (instead of string bucket key)
        .subAggregation(AggregationBuilders.min("versionForSorting").script(numericVersionScript));

    final AggregationBuilder definitionAgg =
      keyFilterAgg.subAggregation(
        tenantsAggregation.subAggregation(
          versionAggregation.subAggregation(
            // return top hit in latest version bucket, should only be one
            AggregationBuilders.topHits(TOP_HITS_AGGREGATION).size(1)
          )));

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .size(0)
      .aggregation(definitionAgg);
    final SearchRequest searchRequest = new SearchRequest(resolveIndexNameForType(type)).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Was not able to fetch latest [%s] definitions for key [%s]",
        type,
        key
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final List<T> result = retrieveResultsFromLatestDefinitionPerTenant(type, searchResponse);

    if (result.isEmpty()) {
      log.debug(
        "Could not find latest [{}] definitions with key [{}]",
        type, key
      );
    }

    return result;
  }

  private List<DefinitionWithTenantIdsDto> getDefinitionWithTenantIdsDtos(final QueryBuilder filterQuery,
                                                                          final String[] definitionIndexNames) {
    // 2.1 group by tenant
    final TermsAggregationBuilder tenantsAggregation =
      terms(TENANT_AGGREGATION)
        .field(DEFINITION_TENANT_ID)
        .size(LIST_FETCH_LIMIT)
        // put `null` values (default tenant) into a dedicated bucket
        .missing(TENANT_NOT_DEFINED_VALUE)
        .order(BucketOrder.key(true));
    // 2.2 group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
      terms(NAME_AGGREGATION)
        .field(DEFINITION_NAME)
        .size(1);
    // 2.3 group by engine
    final TermsAggregationBuilder enginesAggregation =
      terms(ENGINE_AGGREGATION)
        .field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
        .size(LIST_FETCH_LIMIT);
    // 1. group by key and type
    List<CompositeValuesSourceBuilder<?>> keyAndTypeSources = new ArrayList<>();
    keyAndTypeSources.add(new TermsValuesSourceBuilder(DEFINITION_KEY_AGGREGATION).field(DEFINITION_KEY));
    keyAndTypeSources.add(new TermsValuesSourceBuilder(DEFINITION_TYPE_AGGREGATION).field(ElasticsearchConstants.INDEX));

    CompositeAggregationBuilder keyAndTypeAggregation =
      new CompositeAggregationBuilder(DEFINITION_KEY_AND_TYPE_AGGREGATION, keyAndTypeSources)
        .size(configurationService.getEsAggregationBucketLimit())
        .subAggregation(tenantsAggregation)
        .subAggregation(nameAggregation)
        .subAggregation(enginesAggregation);

    final List<ParsedComposite.ParsedBucket> keyAndTypeAggBuckets =
      performSearchAndCollectAllKeyAndTypeBuckets(filterQuery, definitionIndexNames, keyAndTypeAggregation);

    return keyAndTypeAggBuckets.stream()
      .map(keyAndTypeAgg -> {
        final String indexAliasName = (String) (keyAndTypeAgg.getKey()).get(DEFINITION_TYPE_AGGREGATION);
        final String definitionKey = (String) (keyAndTypeAgg.getKey()).get(DEFINITION_KEY_AGGREGATION);
        final Terms tenantResult = keyAndTypeAgg.getAggregations().get(TENANT_AGGREGATION);
        final Terms nameResult = keyAndTypeAgg.getAggregations().get(NAME_AGGREGATION);
        final Terms enginesResult = keyAndTypeAgg.getAggregations().get(ENGINE_AGGREGATION);
        return new DefinitionWithTenantIdsDto(
          definitionKey,
          nameResult.getBuckets()
            .stream()
            .findFirst()
            .map(Terms.Bucket::getKeyAsString)
            .orElse(null),
          resolveDefinitionTypeFromIndexAlias(indexAliasName),
          resolveIsEventProcessFromIndexAlias(indexAliasName),
          tenantResult.getBuckets().stream()
            .map(Terms.Bucket::getKeyAsString)
            // convert null bucket back to a `null` id
            .map(tenantId -> TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
            .collect(toList()),
          enginesResult.getBuckets().stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toSet())
        );
      })
      .collect(toList());
  }

  private void addTenantIdFilter(final Set<String> tenantIds, final BoolQueryBuilder query) {
    if (!CollectionUtils.isEmpty(tenantIds)) {
      final BoolQueryBuilder tenantFilterQuery = boolQuery().minimumShouldMatch(1);

      if (tenantIds.contains(null)) {
        tenantFilterQuery.should(boolQuery().mustNot(existsQuery(TENANT_ID)));
      }

      final Set<String> nonNullValues = tenantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
      if (!nonNullValues.isEmpty()) {
        tenantFilterQuery.should(termsQuery(TENANT_ID, nonNullValues));
      }

      query.filter(tenantFilterQuery);
    }
  }

  private List<ParsedComposite.ParsedBucket> performSearchAndCollectAllKeyAndTypeBuckets
    (final QueryBuilder filterQuery,
     final String[] definitionIndexNames,
     CompositeAggregationBuilder keyAggregation) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .size(0)
      .aggregation(keyAggregation);

    SearchRequest searchRequest = new SearchRequest(definitionIndexNames)
      .source(searchSourceBuilder);

    List<ParsedComposite.ParsedBucket> keyAndTypeAggBuckets = new ArrayList<>();
    try {
      SearchResponse searchResponse = esClient.search(searchRequest);
      ParsedComposite keyAndTypeAggregationResult = searchResponse.getAggregations()
        .get(DEFINITION_KEY_AND_TYPE_AGGREGATION);
      while (!keyAndTypeAggregationResult.getBuckets().isEmpty()) {
        keyAndTypeAggBuckets.addAll(keyAndTypeAggregationResult.getBuckets());

        keyAggregation = keyAggregation.aggregateAfter(keyAndTypeAggregationResult.afterKey());
        searchSourceBuilder = new SearchSourceBuilder()
          .query(filterQuery)
          .size(0)
          .aggregation(keyAggregation);
        searchRequest = new SearchRequest(definitionIndexNames)
          .source(searchSourceBuilder);
        searchResponse = esClient.search(searchRequest);
        keyAndTypeAggregationResult = searchResponse.getAggregations()
          .get(DEFINITION_KEY_AND_TYPE_AGGREGATION);
      }
    } catch (IOException e) {
      final String reason = "Was not able to fetch definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return keyAndTypeAggBuckets;
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> retrieveResultsFromLatestDefinitionPerTenant(
    final DefinitionType type,
    final SearchResponse searchResponse) {
    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    List<T> results = new ArrayList<>();
    final ParsedFilter filteredDefsAgg = searchResponse.getAggregations().get(DEFINITION_KEY_FILTER_AGGREGATION);
    final ParsedStringTerms tenantsAgg = filteredDefsAgg.getAggregations().get(TENANT_AGGREGATION);

    // There should be max. one version bucket in each tenant bucket containing the latest definition for this tenant
    for (Terms.Bucket tenantBucket : tenantsAgg.getBuckets()) {
      final ParsedStringTerms versionsAgg = tenantBucket
        .getAggregations()
        .get(VERSION_AGGREGATION);
      for (Terms.Bucket b : versionsAgg.getBuckets()) {
        final ParsedTopHits topHits = b.getAggregations().get(TOP_HITS_AGGREGATION);
        results.addAll(ElasticsearchReaderUtil.mapHits(
          topHits.getHits(),
          1,
          typeClass,
          createMappingFunctionForDefinitionType(typeClass)
        ));
      }
    }

    return results;
  }

  private <T extends DefinitionOptimizeResponseDto> Function<SearchHit, T> createMappingFunctionForDefinitionType(
    final Class<T> type) {
    return hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        T definitionDto = objectMapper.readValue(sourceAsString, type);
        if (ProcessDefinitionOptimizeDto.class.equals(type)) {
          ProcessDefinitionOptimizeDto processDefinition = (ProcessDefinitionOptimizeDto) definitionDto;
          processDefinition.setType(DefinitionType.PROCESS);
          processDefinition.setEventBased(resolveIsEventProcessFromIndexAlias(hit.getIndex()));
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

  private boolean resolveIsEventProcessFromIndexAlias(String indexName) {
    return indexName.equals(getOptimizeIndexNameForIndex(new EventProcessDefinitionIndex()));
  }

  private String getOptimizeIndexNameForIndex(final DefaultIndexMappingCreator index) {
    return esClient.getIndexNameService().getOptimizeIndexNameWithVersion(index);
  }

  private String[] resolveIndexNameForType(final DefinitionType type) {
    if (type == null) {
      return ALL_DEFINITION_INDEXES;
    }

    switch (type) {
      case PROCESS:
        return new String[]{PROCESS_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME};
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

  private String resolveDefinitionIdFieldFromType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_DEFINITION_ID;
      case DECISION:
        return DECISION_DEFINITION_ID;
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private <T extends DefinitionOptimizeResponseDto> Class<T> resolveDefinitionClassFromType(final DefinitionType type) {
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
