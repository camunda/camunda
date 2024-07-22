/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_NAME;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_TENANT_ID;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION_TAG;
import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.TENANT_ID;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToLatestParticularVersion;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.index.DecisionDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.NotFoundException;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(ElasticSearchCondition.class)
public class DefinitionReaderES implements DefinitionReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(
      final DefinitionType type, final String key) {
    return getDefinitionWithAvailableTenants(type, key, null, null);
  }

  @Override
  public Optional<DefinitionWithTenantIdsDto> getDefinitionWithAvailableTenants(
      final DefinitionType type,
      final String key,
      final List<String> versions,
      final Supplier<String> latestVersionSupplier) {
    if (type == null || key == null) {
      return Optional.empty();
    }
    final BoolQueryBuilder query =
        QueryBuilders.boolQuery()
            .must(termQuery(DEFINITION_KEY, key))
            .must(termQuery(DEFINITION_DELETED, false));

    addVersionFilterToQuery(versions, latestVersionSupplier, query);

    return getDefinitionWithTenantIdsDtos(query, type).stream().findFirst();
  }

  @Override
  public List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(
      final DefinitionType type, final Set<String> keys, final Set<String> tenantIds) {
    final BoolQueryBuilder filterQuery = boolQuery();
    filterQuery.filter(
        boolQuery()
            .minimumShouldMatch(1)
            .must(termQuery(DEFINITION_DELETED, false))
            // use separate 'should' queries as definition type may be null (returning both process
            // and decision)
            .should(existsQuery(PROCESS_DEFINITION_XML))
            .should(existsQuery(DECISION_DEFINITION_XML)));

    if (!CollectionUtils.isEmpty(keys)) {
      filterQuery.filter(termsQuery(DEFINITION_KEY, keys));
    }

    addTenantIdFilter(tenantIds, filterQuery);

    return getDefinitionWithTenantIdsDtos(filterQuery, type);
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedDefinitions(
      final DefinitionType type, final boolean withXml) {
    return getDefinitions(type, true, withXml, false);
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto>
      Optional<T> getFirstFullyImportedDefinitionFromTenantsIfAvailable(
          final DefinitionType type,
          final String definitionKey,
          final List<String> definitionVersions,
          final List<String> tenantIds) {
    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }

    final String mostRecentValidVersion =
        DefinitionVersionHandlingUtil.convertToLatestParticularVersion(
            definitionVersions, () -> getLatestVersionToKey(type, definitionKey));

    Optional<T> definition = Optional.empty();
    for (final String tenantId : tenantIds) {
      definition =
          getFullyImportedDefinition(type, definitionKey, mostRecentValidVersion, tenantId);
      if (definition.isPresent()) {
        // return the first found definition
        break;
      }
    }
    return definition;
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto>
      List<T> getLatestFullyImportedDefinitionsFromTenantsIfAvailable(
          final DefinitionType type, final String definitionKey) {
    if (definitionKey == null) {
      return Collections.emptyList();
    }

    return getLatestFullyImportedDefinitionPerTenant(type, definitionKey);
  }

  @Override
  public Set<String> getDefinitionEngines(final DefinitionType type, final String definitionKey) {
    final TermsAggregationBuilder enginesAggregation =
        terms(ENGINE_AGGREGATION)
            .field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
            .size(LIST_FETCH_LIMIT);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .must(termQuery(resolveDefinitionKeyFieldFromType(type), definitionKey))
                    .must(termQuery(DEFINITION_DELETED, false)))
            // no search results needed, we only need the aggregation
            .size(0)
            .aggregation(enginesAggregation);

    final SearchRequest searchRequest =
        new SearchRequest(
                DefinitionType.PROCESS.equals(type)
                    ? PROCESS_DEFINITION_INDEX_NAME
                    : DECISION_DEFINITION_INDEX_NAME)
            .source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch engines for definition key [%s] and type [%s]",
              definitionKey, type);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getAggregations().<Terms>get(ENGINE_AGGREGATION).getBuckets().stream()
        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
        .collect(Collectors.toSet());
  }

  @Override
  public Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    // 2.2 group by name (should only be one)
    final TermsAggregationBuilder nameAggregation =
        terms(NAME_AGGREGATION).field(DEFINITION_NAME).size(1);
    // 2.1 group by engine
    final TermsAggregationBuilder enginesAggregation =
        terms(ENGINE_AGGREGATION)
            .field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
            .size(LIST_FETCH_LIMIT);
    // 1. group by key, type and tenant (composite aggregation)
    final List<CompositeValuesSourceBuilder<?>> keyAndTypeAndTenantSources = new ArrayList<>();
    keyAndTypeAndTenantSources.add(
        new TermsValuesSourceBuilder(TENANT_AGGREGATION)
            .field(DEFINITION_TENANT_ID)
            .missingBucket(true)
            .order(SortOrder.ASC));
    keyAndTypeAndTenantSources.add(
        new TermsValuesSourceBuilder(DEFINITION_KEY_AGGREGATION).field(DEFINITION_KEY));
    keyAndTypeAndTenantSources.add(
        new TermsValuesSourceBuilder(DEFINITION_TYPE_AGGREGATION).field(DatabaseConstants.INDEX));

    final CompositeAggregationBuilder keyAndTypeAndTenantAggregation =
        new CompositeAggregationBuilder(
                DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION, keyAndTypeAndTenantSources)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .subAggregation(nameAggregation)
            .subAggregation(enginesAggregation);

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(termQuery(DEFINITION_DELETED, false))
            .aggregation(keyAndTypeAndTenantAggregation)
            .size(0);

    final SearchRequest searchRequest =
        new SearchRequest(ALL_DEFINITION_INDEXES).source(searchSourceBuilder);

    final Map<String, List<ParsedComposite.ParsedBucket>> keyAndTypeAggBucketsByTenantId =
        new HashMap<>();
    ElasticsearchCompositeAggregationScroller.create()
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

    final Map<String, TenantIdWithDefinitionsDto> resultMap = new HashMap<>();
    keyAndTypeAggBucketsByTenantId.forEach(
        (key, value) -> {
          // convert not defined bucket back to a `null` id
          final String tenantId = TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(key) ? null : key;
          final List<SimpleDefinitionDto> simpleDefinitionDtos =
              value.stream()
                  .map(
                      parsedBucket -> {
                        final String indexAliasName =
                            (String) (parsedBucket.getKey().get(DEFINITION_TYPE_AGGREGATION));
                        final String definitionKey =
                            (String) (parsedBucket.getKey().get(DEFINITION_KEY_AGGREGATION));
                        final String definitionName =
                            ((Terms) parsedBucket.getAggregations().get(NAME_AGGREGATION))
                                .getBuckets().stream()
                                    .findFirst()
                                    .map(Terms.Bucket::getKeyAsString)
                                    .orElse(null);
                        final Terms enginesResult =
                            parsedBucket.getAggregations().get(ENGINE_AGGREGATION);
                        return new SimpleDefinitionDto(
                            definitionKey,
                            definitionName,
                            resolveDefinitionTypeFromIndexAlias(indexAliasName),
                            enginesResult.getBuckets().stream()
                                .map(Terms.Bucket::getKeyAsString)
                                .collect(Collectors.toSet()));
                      })
                  .toList();
          resultMap.put(tenantId, new TenantIdWithDefinitionsDto(tenantId, simpleDefinitionDtos));
        });

    return resultMap;
  }

  @Override
  public String getLatestVersionToKey(final DefinitionType type, final String key) {
    log.debug("Fetching latest [{}] definition for key [{}]", type, key);

    final Script script =
        createDefaultScript(
            "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)");
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .must(termQuery(resolveDefinitionKeyFieldFromType(type), key))
                    .must(termQuery(DEFINITION_DELETED, false)))
            .sort(
                SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER)
                    .order(SortOrder.DESC))
            .size(1);

    final SearchRequest searchRequest =
        new SearchRequest(resolveIndexNameForType(type)).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch latest [%s] definition for key [%s]", type, key);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 1) {
      final Map<String, Object> sourceAsMap = searchResponse.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(resolveVersionFieldFromType(type))) {
        return sourceAsMap.get(resolveVersionFieldFromType(type)).toString();
      }
    } else {
      throw new NotFoundException("Could not find latest version of definition with key: " + key);
    }
    throw new OptimizeRuntimeException(
        "Unable to retrieve latest version for " + type + " definition key: " + key);
  }

  @Override
  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      final DefinitionType type, final String key, final Set<String> tenantIds) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(termQuery(DEFINITION_KEY, key))
            .filter(termQuery(DEFINITION_DELETED, false));
    addTenantIdFilter(tenantIds, filterQuery);

    final TermsAggregationBuilder versionTagAggregation =
        terms(VERSION_TAG_AGGREGATION)
            .field(DEFINITION_VERSION_TAG)
            // there should be only one tag, and for duplicate entries we accept that just one wins
            .size(1);
    final TermsAggregationBuilder versionAggregation =
        terms(VERSION_AGGREGATION)
            .field(DEFINITION_VERSION)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .subAggregation(versionTagAggregation);

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(filterQuery).aggregation(versionAggregation).size(0);
    final SearchRequest searchRequest =
        new SearchRequest(resolveIndexNameForType(type)).source(searchSourceBuilder);
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch [%s] definition versions with key [%s], tenantIds [%s]",
              type, key, tenantIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse.getAggregations().<Terms>get(VERSION_AGGREGATION).getBuckets().stream()
        .map(
            versionBucket -> {
              final String version = versionBucket.getKeyAsString();
              final Terms versionTags =
                  versionBucket.getAggregations().get(VERSION_TAG_AGGREGATION);
              final String versionTag =
                  versionTags.getBuckets().stream()
                      .findFirst()
                      .map(MultiBucketsAggregation.Bucket::getKeyAsString)
                      .orElse(null);
              return new DefinitionVersionResponseDto(version, versionTag);
            })
        .sorted(Comparator.comparing(DefinitionVersionResponseDto::getVersion).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type,
      final boolean fullyImported,
      final boolean withXml,
      final boolean includeDeleted) {
    return getDefinitions(type, Collections.emptySet(), fullyImported, withXml, includeDeleted);
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type,
      final Set<String> definitionKeys,
      final boolean fullyImported,
      final boolean withXml,
      final boolean includeDeleted) {
    final String xmlField = resolveXmlFieldFromType(type);
    final BoolQueryBuilder rootQuery =
        boolQuery().must(fullyImported ? existsQuery(xmlField) : matchAllQuery());
    final BoolQueryBuilder filteredQuery = rootQuery.must(matchAllQuery());
    if (!includeDeleted) {
      filteredQuery.must(termQuery(DEFINITION_DELETED, false));
    }
    if (!definitionKeys.isEmpty()) {
      filteredQuery.must(
          termsQuery(resolveDefinitionKeyFieldFromType(type), definitionKeys.toArray()));
    }
    return getDefinitions(type, filteredQuery, withXml);
  }

  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type, final BoolQueryBuilder filteredQuery, final boolean withXml) {
    final String xmlField = resolveXmlFieldFromType(type);
    final String[] fieldsToExclude = withXml ? null : new String[] {xmlField};
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(filteredQuery)
            .size(LIST_FETCH_LIMIT)
            .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
        new SearchRequest(resolveIndexNameForType(type))
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String errorMsg =
          String.format("Was not able to retrieve definitions of type %s", type);
      log.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        typeClass,
        createMappingFunctionForDefinitionType(typeClass),
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  private void addVersionFilterToQuery(
      final List<String> versions,
      final Supplier<String> latestVersionSupplier,
      final BoolQueryBuilder filterQuery) {
    // If no versions were given or if 'all' is among the versions, then no filtering is needed
    if (!CollectionUtils.isEmpty(versions)
        && !DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(versions)) {
      filterQuery.filter(
          termsQuery(
              DEFINITION_VERSION,
              versions.stream()
                  .map(version -> convertToLatestParticularVersion(version, latestVersionSupplier))
                  .collect(Collectors.toSet())));
    }
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
        convertToLatestParticularVersion(
            definitionVersion, () -> getLatestVersionToKey(type, definitionKey));
    final BoolQueryBuilder query =
        boolQuery()
            .must(termQuery(resolveDefinitionKeyFieldFromType(type), definitionKey))
            .must(termQuery(resolveVersionFieldFromType(type), validVersion))
            .must(termQuery(DEFINITION_DELETED, false))
            .must(existsQuery(resolveXmlFieldFromType(type)));

    if (tenantId != null) {
      query.must(termQuery(TENANT_ID, tenantId));
    } else {
      query.mustNot(existsQuery(TENANT_ID));
    }

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    final SearchRequest searchRequest =
        new SearchRequest(resolveIndexNameForType(type)).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch [%s] definition with key [%s], version [%s] and tenantId [%s]",
              type, definitionKey, validVersion, tenantId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getTotalHits().value == 0L) {
      log.debug(
          "Could not find [{}] definition with key [{}], version [{}] and tenantId [{}]",
          type,
          definitionKey,
          validVersion,
          tenantId);
      return Optional.empty();
    }

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final T definitionOptimizeDto =
        ElasticsearchReaderUtil.mapHits(
                searchResponse.getHits(),
                1,
                typeClass,
                createMappingFunctionForDefinitionType(typeClass))
            .stream()
            .findFirst()
            .orElse(null);
    return Optional.ofNullable(definitionOptimizeDto);
  }

  private <T extends DefinitionOptimizeResponseDto>
      List<T> getLatestFullyImportedDefinitionPerTenant(
          final DefinitionType type, final String key) {
    log.debug(
        "Fetching latest fully imported [{}] definitions for key [{}] on each tenant", type, key);

    final FilterAggregationBuilder keyFilterAgg =
        filter(
            DEFINITION_KEY_FILTER_AGGREGATION,
            boolQuery()
                .must(termQuery(resolveDefinitionKeyFieldFromType(type), key))
                .must(termQuery(DEFINITION_DELETED, false))
                .must(existsQuery(resolveXmlFieldFromType(type))));

    final TermsAggregationBuilder tenantsAggregation =
        terms(TENANT_AGGREGATION)
            .field(DEFINITION_TENANT_ID)
            .size(LIST_FETCH_LIMIT)
            .missing(TENANT_NOT_DEFINED_VALUE);

    final Script numericVersionScript =
        createDefaultScript(
            "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)");

    final TermsAggregationBuilder versionAggregation =
        terms(VERSION_AGGREGATION)
            .field(DEFINITION_VERSION)
            .size(1) // only return bucket for latest version
            .order(BucketOrder.aggregation("versionForSorting", false))
            // custom sort agg to sort by numeric version value (instead of string bucket key)
            .subAggregation(
                AggregationBuilders.min("versionForSorting").script(numericVersionScript));

    final AggregationBuilder definitionAgg =
        keyFilterAgg.subAggregation(
            tenantsAggregation.subAggregation(
                versionAggregation.subAggregation(
                    // return top hit in latest version bucket, should only be one
                    AggregationBuilders.topHits(TOP_HITS_AGGREGATION).size(1))));

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().size(0).aggregation(definitionAgg);
    final SearchRequest searchRequest =
        new SearchRequest(resolveIndexNameForType(type)).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch latest [%s] definitions for key [%s]", type, key);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final List<T> result = retrieveResultsFromLatestDefinitionPerTenant(type, searchResponse);

    if (result.isEmpty()) {
      log.debug("Could not find latest [{}] definitions with key [{}]", type, key);
    }

    return result;
  }

  private List<DefinitionWithTenantIdsDto> getDefinitionWithTenantIdsDtos(
      final QueryBuilder filterQuery, final DefinitionType type) {
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
            .size(1)
            .order(BucketOrder.aggregation("versionForSorting", false))
            // custom sort agg to sort by numeric version value (instead of string bucket key)
            .subAggregation(
                AggregationBuilders.min("versionForSorting")
                    .script(createDefaultScript("Integer.parseInt(doc['version'].value)")));
    // 2.3 group by engine
    final TermsAggregationBuilder enginesAggregation =
        terms(ENGINE_AGGREGATION)
            .field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
            .size(LIST_FETCH_LIMIT);
    // 1. group by key and type
    final List<CompositeValuesSourceBuilder<?>> keyAndTypeSources = new ArrayList<>();
    keyAndTypeSources.add(
        new TermsValuesSourceBuilder(DEFINITION_KEY_AGGREGATION).field(DEFINITION_KEY));
    keyAndTypeSources.add(
        new TermsValuesSourceBuilder(DEFINITION_TYPE_AGGREGATION).field(DatabaseConstants.INDEX));

    final CompositeAggregationBuilder keyAndTypeAggregation =
        new CompositeAggregationBuilder(DEFINITION_KEY_AND_TYPE_AGGREGATION, keyAndTypeSources)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .subAggregation(tenantsAggregation)
            .subAggregation(nameAggregation)
            .subAggregation(enginesAggregation);

    final List<ParsedComposite.ParsedBucket> keyAndTypeAggBuckets =
        performSearchAndCollectAllKeyAndTypeBuckets(
            filterQuery, resolveIndexNameForType(type), keyAndTypeAggregation);

    return keyAndTypeAggBuckets.stream()
        .map(
            keyAndTypeAgg -> {
              final String indexAliasName =
                  (String) (keyAndTypeAgg.getKey()).get(DEFINITION_TYPE_AGGREGATION);
              final String definitionKey =
                  (String) (keyAndTypeAgg.getKey()).get(DEFINITION_KEY_AGGREGATION);
              final Terms tenantResult = keyAndTypeAgg.getAggregations().get(TENANT_AGGREGATION);
              final Terms nameResult = keyAndTypeAgg.getAggregations().get(NAME_AGGREGATION);
              final Terms enginesResult = keyAndTypeAgg.getAggregations().get(ENGINE_AGGREGATION);
              return new DefinitionWithTenantIdsDto(
                  definitionKey,
                  nameResult.getBuckets().stream()
                      .findFirst()
                      .map(Terms.Bucket::getKeyAsString)
                      .orElse(null),
                  resolveDefinitionTypeFromIndexAlias(indexAliasName),
                  tenantResult.getBuckets().stream()
                      .map(Terms.Bucket::getKeyAsString)
                      // convert null bucket back to a `null` id
                      .map(
                          tenantId ->
                              TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
                      .collect(Collectors.toList()),
                  enginesResult.getBuckets().stream()
                      .map(Terms.Bucket::getKeyAsString)
                      .collect(Collectors.toSet()));
            })
        .toList();
  }

  private void addTenantIdFilter(final Set<String> tenantIds, final BoolQueryBuilder query) {
    if (!CollectionUtils.isEmpty(tenantIds)) {
      final BoolQueryBuilder tenantFilterQuery = boolQuery().minimumShouldMatch(1);

      if (tenantIds.contains(null)) {
        tenantFilterQuery.should(boolQuery().mustNot(existsQuery(TENANT_ID)));
      }
      final Set<String> nonNullValues =
          tenantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
      if (!nonNullValues.isEmpty()) {
        tenantFilterQuery.should(termsQuery(TENANT_ID, nonNullValues));
      }
      query.filter(tenantFilterQuery);
    }
  }

  private List<ParsedComposite.ParsedBucket> performSearchAndCollectAllKeyAndTypeBuckets(
      final QueryBuilder filterQuery,
      final String[] definitionIndexNames,
      CompositeAggregationBuilder keyAggregation) {
    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(filterQuery).size(0).aggregation(keyAggregation);

    SearchRequest searchRequest =
        new SearchRequest(definitionIndexNames).source(searchSourceBuilder);

    final List<ParsedComposite.ParsedBucket> keyAndTypeAggBuckets = new ArrayList<>();
    try {
      SearchResponse searchResponse = esClient.search(searchRequest);
      ParsedComposite keyAndTypeAggregationResult =
          searchResponse.getAggregations().get(DEFINITION_KEY_AND_TYPE_AGGREGATION);
      while (!keyAndTypeAggregationResult.getBuckets().isEmpty()) {
        keyAndTypeAggBuckets.addAll(keyAndTypeAggregationResult.getBuckets());

        keyAggregation = keyAggregation.aggregateAfter(keyAndTypeAggregationResult.afterKey());
        searchSourceBuilder =
            new SearchSourceBuilder().query(filterQuery).size(0).aggregation(keyAggregation);
        searchRequest = new SearchRequest(definitionIndexNames).source(searchSourceBuilder);
        searchResponse = esClient.search(searchRequest);
        keyAndTypeAggregationResult =
            searchResponse.getAggregations().get(DEFINITION_KEY_AND_TYPE_AGGREGATION);
      }
    } catch (final IOException e) {
      final String reason = "Was not able to fetch definitions.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return keyAndTypeAggBuckets;
  }

  private <T extends DefinitionOptimizeResponseDto>
      List<T> retrieveResultsFromLatestDefinitionPerTenant(
          final DefinitionType type, final SearchResponse searchResponse) {
    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final List<T> results = new ArrayList<>();
    final ParsedFilter filteredDefsAgg =
        searchResponse.getAggregations().get(DEFINITION_KEY_FILTER_AGGREGATION);
    final ParsedStringTerms tenantsAgg = filteredDefsAgg.getAggregations().get(TENANT_AGGREGATION);

    // There should be max. one version bucket in each tenant bucket containing the latest
    // definition for this tenant
    for (final Terms.Bucket tenantBucket : tenantsAgg.getBuckets()) {
      final ParsedStringTerms versionsAgg = tenantBucket.getAggregations().get(VERSION_AGGREGATION);
      for (final Terms.Bucket b : versionsAgg.getBuckets()) {
        final ParsedTopHits topHits = b.getAggregations().get(TOP_HITS_AGGREGATION);
        results.addAll(
            ElasticsearchReaderUtil.mapHits(
                topHits.getHits(),
                1,
                typeClass,
                createMappingFunctionForDefinitionType(typeClass)));
      }
    }
    return results;
  }

  private <T extends DefinitionOptimizeResponseDto>
      Function<SearchHit, T> createMappingFunctionForDefinitionType(final Class<T> type) {
    return hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        final T definitionDto = objectMapper.readValue(sourceAsString, type);
        if (ProcessDefinitionOptimizeDto.class.equals(type)) {
          final ProcessDefinitionOptimizeDto processDefinition =
              (ProcessDefinitionOptimizeDto) definitionDto;
          processDefinition.setType(DefinitionType.PROCESS);
        } else {
          definitionDto.setType(DefinitionType.DECISION);
        }
        return definitionDto;
      } catch (final IOException e) {
        final String reason =
            "While mapping search results to class {} "
                + "it was not possible to deserialize a hit from Elasticsearch!"
                + " Hit response from Elasticsearch: "
                + sourceAsString;
        log.error(reason, type.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
  }

  private DefinitionType resolveDefinitionTypeFromIndexAlias(final String indexName) {
    if (indexName.equals(getOptimizeIndexNameForIndex(new ProcessDefinitionIndexES()))) {
      return DefinitionType.PROCESS;
    } else if (indexName.equals(getOptimizeIndexNameForIndex(new DecisionDefinitionIndexES()))) {
      return DefinitionType.DECISION;
    } else {
      throw new OptimizeRuntimeException("Unexpected definition index name: " + indexName);
    }
  }

  private String getOptimizeIndexNameForIndex(final DefaultIndexMappingCreator index) {
    return esClient.getIndexNameService().getOptimizeIndexNameWithVersion(index);
  }
}
