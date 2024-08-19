/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScript;
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

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.os.OpenSearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.AggregationDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.schema.index.DecisionDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessDefinitionIndexOS;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
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
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.ScriptSort;
import org.opensearch.client.opensearch._types.ScriptSortType;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.aggregations.FiltersBucket;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketAggregateBase;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class DefinitionReaderOS implements DefinitionReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

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
    final BoolQuery.Builder query =
        new BoolQuery.Builder()
            .must(QueryDSL.term(DEFINITION_KEY, key))
            .must(QueryDSL.term(DEFINITION_DELETED, false));

    addVersionFilterToQuery(versions, latestVersionSupplier, query);
    return getDefinitionWithTenantIdsDtos(query.build().toQuery(), type).stream().findFirst();
  }

  @Override
  public List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(
      final DefinitionType type, final Set<String> keys, final Set<String> tenantIds) {
    final BoolQuery.Builder filterQuery = new BoolQuery.Builder();
    filterQuery.filter(
        new BoolQuery.Builder()
            .minimumShouldMatch("1")
            .must(QueryDSL.term(DEFINITION_DELETED, false))
            // use separate 'should' queries as definition type may be null (returning both process
            // and decision)
            .should(QueryDSL.exists(PROCESS_DEFINITION_XML))
            .should(QueryDSL.exists(DECISION_DEFINITION_XML))
            .build()
            .toQuery());

    if (!CollectionUtils.isEmpty(keys)) {
      filterQuery.filter(QueryDSL.terms(DEFINITION_KEY, keys, FieldValue::of));
    }

    addTenantIdFilter(tenantIds, filterQuery);

    return getDefinitionWithTenantIdsDtos(filterQuery.build().toQuery(), type);
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
    final TermsAggregation enginesAggregation =
        AggregationDSL.termAggregation(
            DATA_SOURCE + "." + DataSourceDto.Fields.name, LIST_FETCH_LIMIT);

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(
                DefinitionType.PROCESS.equals(type)
                    ? PROCESS_DEFINITION_INDEX_NAME
                    : DECISION_DEFINITION_INDEX_NAME)
            .query(
                new BoolQuery.Builder()
                    .must(QueryDSL.term(resolveDefinitionKeyFieldFromType(type), definitionKey))
                    .must(QueryDSL.term(DEFINITION_DELETED, false))
                    .build()
                    .toQuery())
            .size(0)
            .aggregations(
                Collections.singletonMap(ENGINE_AGGREGATION, enginesAggregation._toAggregation()));

    final String errorMessage =
        String.format(
            "Was not able to fetch engines for definition key [%s] and type [%s]",
            definitionKey, type);
    final SearchResponse<String> searchResponse =
        osClient.search(searchRequest, String.class, errorMessage);

    return Stream.of(searchResponse.aggregations().get(ENGINE_AGGREGATION).sterms())
        .map(MultiBucketAggregateBase::buckets)
        .flatMap(bucket -> bucket.array().stream())
        .map(StringTermsBucket::key)
        .collect(Collectors.toSet());
  }

  @Override
  public Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    // 2.2 group by name (should only be one)
    final TermsAggregation nameAggregation = AggregationDSL.termAggregation(DEFINITION_NAME, 1);

    // 2.1 group by engine
    final TermsAggregation enginesAggregation =
        AggregationDSL.termAggregation(
            DATA_SOURCE + "." + DataSourceDto.Fields.name, LIST_FETCH_LIMIT);

    // 1. group by key, type and tenant (composite aggregation)
    final List<Map<String, CompositeAggregationSource>> keyAndTypeAndTenantSources =
        new ArrayList<>();
    keyAndTypeAndTenantSources.add(
        Collections.singletonMap(
            TENANT_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(
                    new TermsAggregation.Builder()
                        .missingBucket(true)
                        .field(DEFINITION_TENANT_ID)
                        .build())
                .build()));

    keyAndTypeAndTenantSources.add(
        Collections.singletonMap(
            DEFINITION_KEY_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(new TermsAggregation.Builder().field(DEFINITION_KEY).build())
                .build()));

    keyAndTypeAndTenantSources.add(
        Collections.singletonMap(
            DEFINITION_TYPE_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(new TermsAggregation.Builder().field(DatabaseConstants.INDEX).build())
                .build()));

    final CompositeAggregation keyAndTypeAndTenantAggregation =
        new CompositeAggregation.Builder()
            .sources(keyAndTypeAndTenantSources)
            .size(configurationService.getOpenSearchConfiguration().getAggregationBucketLimit())
            .build();

    final Aggregation complexAggregation =
        Aggregation.of(
            a ->
                a.composite(keyAndTypeAndTenantAggregation)
                    .aggregations(NAME_AGGREGATION, nameAggregation._toAggregation())
                    .aggregations(ENGINE_AGGREGATION, enginesAggregation._toAggregation()));

    final Map<String, List<CompositeBucket>> keyAndTypeAggBucketsByTenantId = new HashMap<>();

    OpenSearchCompositeAggregationScroller.create()
        .setClient(osClient)
        .query(QueryDSL.term(DEFINITION_DELETED, false))
        .index(List.of(ALL_DEFINITION_INDEXES))
        .aggregations(
            Collections.singletonMap(
                DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION, complexAggregation))
        .size(0)
        .setPathToAggregation(DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION)
        .setCompositeBucketConsumer(
            bucket -> {
              final String tenantId = (bucket.key()).get(TENANT_AGGREGATION).to(String.class);
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
                            parsedBucket.key().get(DEFINITION_TYPE_AGGREGATION).to(String.class);
                        final String definitionKey =
                            parsedBucket.key().get(DEFINITION_KEY_AGGREGATION).to(String.class);
                        final String definitionName =
                            parsedBucket
                                .aggregations()
                                .get(NAME_AGGREGATION)
                                .sterms()
                                .buckets()
                                .array()
                                .stream()
                                .findFirst()
                                .map(StringTermsBucket::key)
                                .orElse(null);
                        final Set<String> engines =
                            parsedBucket
                                .aggregations()
                                .get(ENGINE_AGGREGATION)
                                .sterms()
                                .buckets()
                                .array()
                                .stream()
                                .map(StringTermsBucket::key)
                                .collect(Collectors.toSet());
                        return new SimpleDefinitionDto(
                            definitionKey,
                            definitionName,
                            resolveDefinitionTypeFromIndexAlias(indexAliasName),
                            engines);
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

    final SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(List.of(resolveIndexNameForType(type)))
            .size(1)
            .query(
                new BoolQuery.Builder()
                    .must(QueryDSL.term(resolveDefinitionKeyFieldFromType(type), key))
                    .must(QueryDSL.term(DEFINITION_DELETED, false))
                    .build()
                    .toQuery())
            .sort(
                new SortOptions.Builder()
                    .script(
                        new ScriptSort.Builder()
                            .script(script)
                            .order(SortOrder.Desc)
                            .type(ScriptSortType.Number)
                            .build())
                    .build());

    final String errorMessage =
        String.format("Was not able to fetch latest [%s] definition for key [%s]", type, key);
    final SearchResponse<DefinitionOptimizeResponseDto> searchResponse =
        osClient.search(searchBuilder, resolveDefinitionClassFromType(type), errorMessage);

    if (searchResponse.hits().hits().size() == 1) {
      return searchResponse.hits().hits().get(0).source().getVersion();
    } else {
      throw new NotFoundException(
          "Unable to retrieve latest version for " + type + " definition key: " + key);
    }
  }

  @Override
  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      final DefinitionType type, final String key, final Set<String> tenantIds) {
    final BoolQuery.Builder filterQuery =
        new BoolQuery.Builder()
            .filter(QueryDSL.term(DEFINITION_KEY, key))
            .filter(QueryDSL.term(DEFINITION_DELETED, false));

    addTenantIdFilter(tenantIds, filterQuery);

    // there should be only one tag, and for duplicate entries we accept that just one wins
    final TermsAggregation versionTagAggregation =
        AggregationDSL.termAggregation(DEFINITION_VERSION_TAG, 1);

    final TermsAggregation versionAggregation =
        AggregationDSL.termAggregation(
            DEFINITION_VERSION,
            configurationService.getOpenSearchConfiguration().getAggregationBucketLimit());

    final SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(List.of(resolveIndexNameForType(type)))
            .query(filterQuery.build().toQuery())
            .aggregations(
                Collections.singletonMap(
                    VERSION_AGGREGATION,
                    AggregationDSL.withSubaggregations(
                        versionAggregation,
                        Collections.singletonMap(
                            VERSION_TAG_AGGREGATION, versionTagAggregation._toAggregation()))))
            .size(0);

    final String errorMessage =
        String.format(
            "Was not able to fetch [%s] definition versions with key [%s], tenantIds [%s]",
            type, key, tenantIds);

    final SearchResponse<DefinitionVersionResponseDto> searchResponse =
        osClient.search(searchBuilder, DefinitionVersionResponseDto.class, errorMessage);

    return Stream.of(searchResponse.aggregations().get(VERSION_AGGREGATION).sterms())
        .map(MultiBucketAggregateBase::buckets)
        .flatMap(stringTermsBucketBuckets -> stringTermsBucketBuckets.array().stream())
        .map(
            versionBucket -> {
              final String version = versionBucket.key();
              final Aggregate versionTags =
                  versionBucket.aggregations().get(VERSION_TAG_AGGREGATION);
              final String versionTag =
                  Optional.ofNullable(versionTags)
                      .map(Aggregate::sterms)
                      .map(MultiBucketAggregateBase::buckets)
                      .map(Buckets::array)
                      .flatMap(a -> a.stream().findFirst())
                      .map(StringTermsBucket::key)
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
    final BoolQuery.Builder rootQuery =
        new BoolQuery.Builder()
            .must(fullyImported ? QueryDSL.exists(xmlField) : QueryDSL.matchAll());
    final BoolQuery.Builder filteredQuery = rootQuery.must(QueryDSL.matchAll());
    if (!includeDeleted) {
      filteredQuery.must(QueryDSL.term(DEFINITION_DELETED, false));
    }
    if (!definitionKeys.isEmpty()) {
      filteredQuery.must(
          QueryDSL.terms(resolveDefinitionKeyFieldFromType(type), definitionKeys, FieldValue::of));
    }
    return getDefinitions(type, filteredQuery.build(), withXml);
  }

  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type, final BoolQuery filterQuery, final boolean withXml) {
    final String xmlField = resolveXmlFieldFromType(type);
    final List<String> fieldsToExclude = withXml ? Collections.emptyList() : List.of(xmlField);
    final SourceConfig searchSourceBuilder =
        new SourceConfig.Builder()
            .filter(new SourceFilter.Builder().excludes(fieldsToExclude).build())
            .build();

    final SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(List.of(resolveIndexNameForType(type)))
            .query(filterQuery.toQuery())
            .source(searchSourceBuilder)
            .size(LIST_FETCH_LIMIT)
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final OpenSearchDocumentOperations.AggregatedResult<Hit<T>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchBuilder, typeClass);
    } catch (final IOException e) {
      final String errorMsg =
          String.format("Was not able to retrieve definitions of type %s", type);
      log.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(
        scrollResp, createMappingFunctionForDefinitionType(typeClass));
  }

  private void addVersionFilterToQuery(
      final List<String> versions,
      final Supplier<String> latestVersionSupplier,
      final BoolQuery.Builder filterQuery) {
    // If no versions were given or if 'all' is among the versions, then no filtering is needed
    if (!CollectionUtils.isEmpty(versions)
        && !DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(versions)) {
      filterQuery.filter(
          QueryDSL.terms(
              DEFINITION_VERSION,
              versions.stream()
                  .map(version -> convertToLatestParticularVersion(version, latestVersionSupplier))
                  .collect(Collectors.toSet()),
              FieldValue::of));
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
    final BoolQuery.Builder query =
        new BoolQuery.Builder()
            .must(QueryDSL.term(resolveDefinitionKeyFieldFromType(type), definitionKey))
            .must(QueryDSL.term(resolveVersionFieldFromType(type), validVersion))
            .must(QueryDSL.term(DEFINITION_DELETED, false))
            .must(QueryDSL.exists(resolveXmlFieldFromType(type)));

    if (tenantId != null) {
      query.must(QueryDSL.term(TENANT_ID, tenantId));
    } else {
      query.mustNot(QueryDSL.exists(TENANT_ID));
    }

    final SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(List.of(resolveIndexNameForType(type)))
            .query(query.build().toQuery())
            .size(1);

    final Class<T> typeClass = resolveDefinitionClassFromType(type);

    final String errorMessage =
        String.format(
            "Was not able to fetch [%s] definition with key [%s], version [%s] and tenantId [%s]",
            type, definitionKey, validVersion, tenantId);

    final SearchResponse<T> searchResponse =
        osClient.search(searchBuilder, typeClass, errorMessage);

    if (searchResponse.hits().total().value() == 0L) {
      log.debug(
          "Could not find [{}] definition with key [{}], version [{}] and tenantId [{}]",
          type,
          definitionKey,
          validVersion,
          tenantId);
      return Optional.empty();
    }
    return Optional.ofNullable(
        OpensearchReaderUtil.extractResponseValues(
                searchResponse, createMappingFunctionForDefinitionType(typeClass))
            .get(0));
  }

  private <T extends DefinitionOptimizeResponseDto>
      List<T> getLatestFullyImportedDefinitionPerTenant(
          final DefinitionType type, final String key) {
    log.debug(
        "Fetching latest fully imported [{}] definitions for key [{}] on each tenant", type, key);

    final FiltersAggregation keyFilterAgg =
        AggregationDSL.filtersAggregation(
            Collections.singletonMap(
                DEFINITION_KEY_FILTER_AGGREGATION,
                new BoolQuery.Builder()
                    .must(QueryDSL.term(resolveDefinitionKeyFieldFromType(type), key))
                    .must(QueryDSL.term(DEFINITION_DELETED, false))
                    .must(QueryDSL.exists(resolveXmlFieldFromType(type)))
                    .build()
                    .toQuery()));

    final TermsAggregation tenantsAggregation =
        new TermsAggregation.Builder()
            .field(DEFINITION_TENANT_ID)
            .missing(FieldValue.of(TENANT_NOT_DEFINED_VALUE))
            .size(LIST_FETCH_LIMIT)
            .build();

    final Script numericVersionScript =
        createDefaultScript(
            "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)");

    final TermsAggregation versionAggregation =
        new TermsAggregation.Builder()
            .field(DEFINITION_VERSION)
            .size(1)
            // only return bucket for latest version
            .order(Collections.singletonMap("versionForSorting", SortOrder.Desc))
            // custom sort agg to sort by numeric version value (instead of string bucket key)
            .build();

    final Map<String, Aggregation> subAggregations = new HashMap<>();

    subAggregations.put(
        "versionForSorting",
        AggregationBuilders.min().script(numericVersionScript).build()._toAggregation());

    subAggregations.put(
        "topHits", AggregationDSL.topHitsAggregation(1)._toAggregation() // Use size=1
        );

    final Aggregation definitionAgg =
        AggregationDSL.withSubaggregations(
            keyFilterAgg,
            Collections.singletonMap(
                TENANT_AGGREGATION,
                AggregationDSL.withSubaggregations(
                    tenantsAggregation,
                    Collections.singletonMap(
                        VERSION_AGGREGATION,
                        AggregationDSL.withSubaggregations(versionAggregation, subAggregations)))));

    final SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(List.of(resolveIndexNameForType(type)))
            .size(0)
            .aggregations(DEFINITION_KEY_FILTER_AGGREGATION, definitionAgg);

    final String errorMessage =
        String.format("Was not able to fetch latest [%s] definitions for key [%s]", type, key);

    final SearchResponse<T> searchResponse =
        osClient.search(searchBuilder, resolveDefinitionClassFromType(type), errorMessage);

    final List<T> result = retrieveResultsFromLatestDefinitionPerTenant(type, searchResponse);

    if (result.isEmpty()) {
      log.debug("Could not find latest [{}] definitions with key [{}]", type, key);
    }
    return result;
  }

  private List<DefinitionWithTenantIdsDto> getDefinitionWithTenantIdsDtos(
      final Query filterQuery, final DefinitionType type) {
    // 2.1 group by tenant
    final TermsAggregation tenantsAggregation =
        new TermsAggregation.Builder()
            .field(DEFINITION_TENANT_ID)
            .size(LIST_FETCH_LIMIT)
            .missing(FieldValue.of(TENANT_NOT_DEFINED_VALUE))
            .order(Collections.singletonMap("_key", SortOrder.Asc))
            .build();

    // 2.2 group by name (should only be one)
    final TermsAggregation nameAggregation =
        new TermsAggregation.Builder()
            .field(DEFINITION_NAME)
            .size(1)
            .order(Collections.singletonMap("versionForSorting", SortOrder.Desc))
            .build();

    // custom sort agg to sort by numeric version value (instead of string bucket key)
    // "versionForSorting"
    final Aggregation nameAggregationSub =
        AggregationDSL.withSubaggregations(
            nameAggregation,
            Collections.singletonMap(
                "versionForSorting",
                AggregationBuilders.min()
                    .script(createDefaultScript("Integer.parseInt(doc['version'].value)"))
                    .build()
                    ._toAggregation()));

    // 2.3 group by engine
    final TermsAggregation enginesAggregation =
        AggregationDSL.termAggregation(
            DATA_SOURCE + "." + DataSourceDto.Fields.name, LIST_FETCH_LIMIT);
    // 1. group by key and type

    final List<Map<String, CompositeAggregationSource>> keyAndTypeSources = new ArrayList<>();

    keyAndTypeSources.add(
        Collections.singletonMap(
            DEFINITION_KEY_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(new TermsAggregation.Builder().field(DEFINITION_KEY).build())
                .build()));

    keyAndTypeSources.add(
        Collections.singletonMap(
            DEFINITION_TYPE_AGGREGATION,
            new CompositeAggregationSource.Builder()
                .terms(new TermsAggregation.Builder().field(DatabaseConstants.INDEX).build())
                .build()));

    final CompositeAggregation.Builder keyAndTypeCompositeAggregation =
        new CompositeAggregation.Builder()
            .sources(keyAndTypeSources)
            .size(configurationService.getOpenSearchConfiguration().getAggregationBucketLimit());

    final Aggregation.Builder complexKeyAndTypeAggregationBuilder =
        new Aggregation.Builder()
            .aggregations(TENANT_AGGREGATION, tenantsAggregation._toAggregation())
            .aggregations(NAME_AGGREGATION, nameAggregationSub)
            .aggregations(ENGINE_AGGREGATION, enginesAggregation._toAggregation());

    final List<CompositeBucket> keyAndTypeAggBuckets =
        performSearchAndCollectAllKeyAndTypeBuckets(
            filterQuery,
            resolveIndexNameForType(type),
            complexKeyAndTypeAggregationBuilder,
            keyAndTypeCompositeAggregation,
            resolveDefinitionClassFromType(type));

    return keyAndTypeAggBuckets.stream()
        .map(
            keyAndTypeAgg -> {
              final String indexAliasName =
                  keyAndTypeAgg.key().get(DEFINITION_TYPE_AGGREGATION).to(String.class);
              final String definitionKey =
                  keyAndTypeAgg.key().get(DEFINITION_KEY_AGGREGATION).to(String.class);
              final StringTermsAggregate tenantResult =
                  keyAndTypeAgg.aggregations().get(TENANT_AGGREGATION).sterms();
              final StringTermsAggregate nameResult =
                  keyAndTypeAgg.aggregations().get(NAME_AGGREGATION).sterms();
              final StringTermsAggregate enginesResult =
                  keyAndTypeAgg.aggregations().get(ENGINE_AGGREGATION).sterms();
              return new DefinitionWithTenantIdsDto(
                  definitionKey,
                  nameResult.buckets().array().stream()
                      .findFirst()
                      .map(StringTermsBucket::key)
                      .orElse(null),
                  resolveDefinitionTypeFromIndexAlias(indexAliasName),
                  tenantResult.buckets().array().stream()
                      .map(StringTermsBucket::key)
                      // convert null bucket back to a `null` id
                      .map(
                          tenantId ->
                              TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
                      .collect(Collectors.toList()),
                  enginesResult.buckets().array().stream()
                      .map(StringTermsBucket::key)
                      .collect(Collectors.toSet()));
            })
        .toList();
  }

  private void addTenantIdFilter(final Set<String> tenantIds, final BoolQuery.Builder query) {
    if (!CollectionUtils.isEmpty(tenantIds)) {
      final BoolQuery.Builder tenantFilterQuery = new BoolQuery.Builder().minimumShouldMatch("1");

      if (tenantIds.contains(null)) {
        tenantFilterQuery.should(
            new BoolQuery.Builder().mustNot(QueryDSL.exists(TENANT_ID)).build().toQuery());
      }
      final Set<String> nonNullValues =
          tenantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
      if (!nonNullValues.isEmpty()) {
        tenantFilterQuery.should(QueryDSL.terms(TENANT_ID, nonNullValues, FieldValue::of));
      }
      query.filter(tenantFilterQuery.build().toQuery());
    }
  }

  private <T> List<CompositeBucket> performSearchAndCollectAllKeyAndTypeBuckets(
      final Query filterQuery,
      final String[] definitionIndexNames,
      final Aggregation.Builder keyTypeAggregationBuilder,
      final CompositeAggregation.Builder keyAndTypeCompositeAggregation,
      final Class<T> typeResponse) {

    final CompositeAggregation compositeAggregation = keyAndTypeCompositeAggregation.build();

    final Aggregation keyTypeAggregation =
        keyTypeAggregationBuilder.composite(compositeAggregation).build();

    SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(List.of(definitionIndexNames))
            .aggregations(DEFINITION_KEY_AND_TYPE_AGGREGATION, keyTypeAggregation)
            .query(filterQuery)
            .size(0);

    final List<CompositeBucket> keyAndTypeAggBuckets = new ArrayList<>();

    final String errorMessage =
        String.format(
            "Was not able to fetch definitions with composite aggregation for type [%s]",
            typeResponse);
    SearchResponse<T> searchResponse =
        osClient.search(searchReqBuilder, typeResponse, errorMessage);
    CompositeAggregate keyAndTypeAggregationResult =
        searchResponse.aggregations().get(DEFINITION_KEY_AND_TYPE_AGGREGATION).composite();

    while (!keyAndTypeAggregationResult.buckets().array().isEmpty()) {
      keyAndTypeAggBuckets.addAll(keyAndTypeAggregationResult.buckets().array());

      final Map<String, String> keysToTypes =
          keyAndTypeAggregationResult.afterKey().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().to(String.class)));

      final Aggregation.Builder keyTypeAggregationTempBuilder =
          new Aggregation.Builder().aggregations(keyTypeAggregation.aggregations());
      final Aggregation compositeWithAfterAggregation =
          keyTypeAggregationTempBuilder
              .composite(
                  new CompositeAggregation.Builder()
                      .sources(compositeAggregation.sources())
                      .size(compositeAggregation.size())
                      .after(keysToTypes)
                      .build())
              .build();

      searchReqBuilder =
          new SearchRequest.Builder()
              .index(List.of(definitionIndexNames))
              .query(filterQuery)
              .aggregations(DEFINITION_KEY_AND_TYPE_AGGREGATION, compositeWithAfterAggregation)
              .size(0);

      searchResponse = osClient.search(searchReqBuilder, typeResponse, errorMessage);
      keyAndTypeAggregationResult =
          searchResponse.aggregations().get(DEFINITION_KEY_AND_TYPE_AGGREGATION).composite();
    }
    return keyAndTypeAggBuckets;
  }

  private <T extends DefinitionOptimizeResponseDto>
      List<T> retrieveResultsFromLatestDefinitionPerTenant(
          final DefinitionType type, final SearchResponse<T> searchResponse) {
    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final List<T> results = new ArrayList<>();
    final FiltersBucket filteredDefsAgg =
        searchResponse
            .aggregations()
            .get(DEFINITION_KEY_FILTER_AGGREGATION)
            .filters()
            .buckets()
            .keyed()
            .get(DEFINITION_KEY_FILTER_AGGREGATION);

    final List<StringTermsBucket> tenantsAgg =
        filteredDefsAgg.aggregations().get(TENANT_AGGREGATION).sterms().buckets().array();

    // There should be max. one version bucket in each tenant bucket containing the latest
    // definition for this tenant
    for (final StringTermsBucket tenantBucket : tenantsAgg) {
      final List<StringTermsBucket> versionsAgg =
          tenantBucket.aggregations().get(VERSION_AGGREGATION).sterms().buckets().array();

      for (final StringTermsBucket b : versionsAgg) {
        final TopHitsAggregate topHits = b.aggregations().get(TOP_HITS_AGGREGATION).topHits();
        results.addAll(
            OpensearchReaderUtil.mapHits(
                topHits.hits(), 1, typeClass, createMappingFunctionForDefinitionType(typeClass)));
      }
    }
    return results;
  }

  private DefinitionType resolveDefinitionTypeFromIndexAlias(final String indexName) {
    if (indexName.equals(getOptimizeIndexNameForIndex(new ProcessDefinitionIndexOS()))) {
      return DefinitionType.PROCESS;
    } else if (indexName.equals(getOptimizeIndexNameForIndex(new DecisionDefinitionIndexOS()))) {
      return DefinitionType.DECISION;
    } else {
      throw new OptimizeRuntimeException("Unexpected definition index name: " + indexName);
    }
  }

  private String getOptimizeIndexNameForIndex(final DefaultIndexMappingCreator index) {
    return osClient.getIndexNameService().getOptimizeIndexNameWithVersion(index);
  }

  private <T extends DefinitionOptimizeResponseDto>
      Function<Hit<T>, T> createMappingFunctionForDefinitionType(final Class<T> type) {
    return hit -> {
      try {
        final T definitionDto = hit.source();
        if (ProcessDefinitionOptimizeDto.class.equals(type)) {
          final ProcessDefinitionOptimizeDto processDefinition =
              (ProcessDefinitionOptimizeDto) definitionDto;
          processDefinition.setType(DefinitionType.PROCESS);
        } else {
          definitionDto.setType(DefinitionType.DECISION);
        }
        return definitionDto;
      } catch (final Exception e) {
        final String reason =
            "While mapping search results to class {} "
                + "it was not possible to deserialize a hit from OpenSearch!"
                + " Hit response from OpenSearch: "
                + hit.id();
        log.error(reason, type.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
  }
}
