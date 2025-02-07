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

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.TopHitsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.ElasticsearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.schema.index.DecisionDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
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
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DefinitionReaderES implements DefinitionReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DefinitionReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public DefinitionReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

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
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> {
          b.must(m -> m.term(t -> t.field(DEFINITION_KEY).value(key)))
              .must(m -> m.term(t -> t.field(DEFINITION_DELETED).value(false)));
          addVersionFilterToQuery(versions, latestVersionSupplier, b);
          return b;
        });

    return getDefinitionWithTenantIdsDtos(builder, type).stream().findFirst();
  }

  @Override
  public List<DefinitionWithTenantIdsDto> getFullyImportedDefinitionsWithTenantIds(
      final DefinitionType type, final Set<String> keys, final Set<String> tenantIds) {

    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> {
          b.minimumShouldMatch("1")
              .must(m -> m.term(t -> t.field(DEFINITION_DELETED).value(false)))
              // use separate 'should' queries as definition type may be null
              // (returning both process and decision)
              .should(s -> s.exists(e -> e.field(PROCESS_DEFINITION_XML)))
              .should(s -> s.exists(e -> e.field(DECISION_DEFINITION_XML)));
          if (!CollectionUtils.isEmpty(keys)) {
            b.filter(
                f ->
                    f.terms(
                        t ->
                            t.field(DEFINITION_KEY)
                                .terms(
                                    tt -> tt.value(keys.stream().map(FieldValue::of).toList()))));
          }
          addTenantIdFilter(tenantIds, b);
          return b;
        });

    return getDefinitionWithTenantIdsDtos(builder, type);
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
    final TermsAggregation termsAggregation =
        TermsAggregation.of(
            t -> t.field(DATA_SOURCE + "." + DataSourceDto.Fields.name).size(LIST_FETCH_LIMIT));
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(
                        esClient,
                        DefinitionType.PROCESS.equals(type)
                            ? PROCESS_DEFINITION_INDEX_NAME
                            : DECISION_DEFINITION_INDEX_NAME)
                    .query(
                        q ->
                            q.bool(
                                b ->
                                    b.must(
                                            m ->
                                                m.term(
                                                    t ->
                                                        t.field(
                                                                resolveDefinitionKeyFieldFromType(
                                                                    type))
                                                            .value(definitionKey)))
                                        .must(
                                            m ->
                                                m.term(
                                                    t ->
                                                        t.field(DEFINITION_DELETED).value(false)))))
                    // no search results needed, we only need the aggregation
                    .size(0)
                    .aggregations(
                        ENGINE_AGGREGATION, Aggregation.of(a -> a.terms(termsAggregation))));

    final SearchResponse<?> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch engines for definition key [%s] and type [%s]",
              definitionKey, type);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final Buckets<StringTermsBucket> buckets =
        searchResponse.aggregations().get(ENGINE_AGGREGATION).sterms().buckets();
    if (buckets.isArray()) {
      return buckets.array().stream().map(b -> b.key().stringValue()).collect(Collectors.toSet());
    } else {
      return buckets.keyed().keySet();
    }
  }

  @Override
  public Map<String, TenantIdWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    final Function<Map<String, FieldValue>, SearchRequest> aggregationRequestWithAfterKeys =
        (map) -> {
          // 2.2 group by name (should only be one)
          // Name NAME_AGGREGATION
          final TermsAggregation nameAggregation =
              TermsAggregation.of(b -> b.field(DEFINITION_NAME).size(1));

          // 2.1 group by engine
          // Name ENGINE_AGGREGATION
          final TermsAggregation enginesAggregation =
              TermsAggregation.of(
                  b ->
                      b.field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
                          .size(LIST_FETCH_LIMIT));

          // 1. group by key, type and tenant (composite aggregation)
          final List<Map<String, CompositeAggregationSource>> keyAndTypeAndTenantSources =
              new ArrayList<>();
          keyAndTypeAndTenantSources.add(
              Map.of(
                  TENANT_AGGREGATION,
                  CompositeAggregationSource.of(
                      c ->
                          c.terms(
                              t ->
                                  t.field(DEFINITION_TENANT_ID)
                                      .missingBucket(true)
                                      .order(SortOrder.Asc)))));
          keyAndTypeAndTenantSources.add(
              Map.of(
                  DEFINITION_KEY_AGGREGATION,
                  CompositeAggregationSource.of(
                      c ->
                          c.terms(
                              t ->
                                  t.field(DEFINITION_KEY)
                                      .missingBucket(false)
                                      .order(SortOrder.Asc)))));
          keyAndTypeAndTenantSources.add(
              Map.of(
                  DEFINITION_TYPE_AGGREGATION,
                  CompositeAggregationSource.of(
                      c ->
                          c.terms(
                              t ->
                                  t.field(DatabaseConstants.INDEX)
                                      .missingBucket(false)
                                      .order(SortOrder.Asc)))));

          final Aggregation keyAndTypeAndTenantAggregation =
              Aggregation.of(
                  a ->
                      a.composite(
                              CompositeAggregation.of(
                                  b -> {
                                    b.sources(keyAndTypeAndTenantSources)
                                        .size(
                                            configurationService
                                                .getElasticSearchConfiguration()
                                                .getAggregationBucketLimit());
                                    if (map != null) {
                                      b.after(map);
                                    }
                                    return b;
                                  }))
                          .aggregations(
                              NAME_AGGREGATION, Aggregation.of(a1 -> a1.terms(nameAggregation)))
                          .aggregations(
                              ENGINE_AGGREGATION,
                              Aggregation.of(a1 -> a1.terms(enginesAggregation))));

          return OptimizeSearchRequestBuilderES.of(
              s ->
                  s.optimizeIndex(esClient, ALL_DEFINITION_INDEXES)
                      .query(q -> q.term(t -> t.field(DEFINITION_DELETED).value(false)))
                      .aggregations(
                          DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION,
                          keyAndTypeAndTenantAggregation)
                      .size(0));
        };

    final Map<String, List<CompositeBucket>> keyAndTypeAggBucketsByTenantId = new HashMap<>();

    ElasticsearchCompositeAggregationScroller.create()
        .setEsClient(esClient)
        .setSearchRequest(aggregationRequestWithAfterKeys.apply(null))
        .setFunction(aggregationRequestWithAfterKeys)
        .setPathToAggregation(DEFINITION_KEY_AND_TYPE_AND_TENANT_AGGREGATION)
        .setCompositeBucketConsumer(
            bucket -> {
              final FieldValue tenantIdFV = bucket.key().get(TENANT_AGGREGATION);
              final String tenantId = tenantIdFV.isNull() ? null : tenantIdFV.stringValue();
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
                            parsedBucket.key().get(DEFINITION_TYPE_AGGREGATION).stringValue();
                        final String definitionKey =
                            parsedBucket.key().get(DEFINITION_KEY_AGGREGATION).stringValue();
                        final String definitionName =
                            parsedBucket
                                .aggregations()
                                .get(NAME_AGGREGATION)
                                .sterms()
                                .buckets()
                                .array()
                                .stream()
                                .findFirst()
                                .map(b -> b.key().stringValue())
                                .orElse(null);
                        final StringTermsAggregate enginesResult =
                            parsedBucket.aggregations().get(ENGINE_AGGREGATION).sterms();
                        return new SimpleDefinitionDto(
                            definitionKey,
                            definitionName,
                            resolveDefinitionTypeFromIndexAlias(indexAliasName),
                            enginesResult.buckets().array().stream()
                                .map(b -> b.key().stringValue())
                                .collect(Collectors.toSet()));
                      })
                  .toList();
          resultMap.put(tenantId, new TenantIdWithDefinitionsDto(tenantId, simpleDefinitionDtos));
        });

    return resultMap;
  }

  @Override
  public String getLatestVersionToKey(final DefinitionType type, final String key) {
    LOG.debug("Fetching latest [{}] definition for key [{}]", type, key);

    final Script script =
        createDefaultScript(
            "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)");

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, resolveIndexNameForType(type))
                    .query(
                        q ->
                            q.bool(
                                b ->
                                    b.must(
                                            m ->
                                                m.term(
                                                    t ->
                                                        t.field(
                                                                resolveDefinitionKeyFieldFromType(
                                                                    type))
                                                            .value(key)))
                                        .must(
                                            m ->
                                                m.term(
                                                    t ->
                                                        t.field(DEFINITION_DELETED).value(false)))))
                    .sort(
                        so ->
                            so.script(
                                ss ->
                                    ss.script(script)
                                        .order(SortOrder.Desc)
                                        .type(ScriptSortType.Number)))
                    .size(1));

    final SearchResponse<Map> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, Map.class);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch latest [%s] definition for key [%s]", type, key);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.hits().hits().size() == 1) {
      final Map<String, Object> sourceAsMap = searchResponse.hits().hits().get(0).source();
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
    final BoolQuery.Builder filterQuery = new BoolQuery.Builder();
    filterQuery
        .filter(f -> f.term(t -> t.field(DEFINITION_KEY).value(key)))
        .filter(f -> f.term(t -> t.field(DEFINITION_DELETED).value(false)));

    addTenantIdFilter(tenantIds, filterQuery);

    final TermsAggregation versionTagAggregation =
        TermsAggregation.of(
            t ->
                t.field(DEFINITION_VERSION_TAG)
                    // there should be only one tag, and for duplicate entries we accept that just
                    // one wins
                    .size(1));

    final Aggregation versionAggregation =
        Aggregation.of(
            a ->
                a.terms(
                        TermsAggregation.of(
                            t ->
                                t.field(DEFINITION_VERSION)
                                    .size(
                                        configurationService
                                            .getElasticSearchConfiguration()
                                            .getAggregationBucketLimit())))
                    .aggregations(
                        VERSION_TAG_AGGREGATION,
                        Aggregation.of(a1 -> a1.terms(versionTagAggregation))));

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, resolveIndexNameForType(type))
                    .query(Query.of(q -> q.bool(filterQuery.build())))
                    .aggregations(VERSION_AGGREGATION, versionAggregation)
                    .size(0));
    final SearchResponse<DefinitionVersionResponseDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, DefinitionVersionResponseDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch [%s] definition versions with key [%s], tenantIds [%s]",
              type, key, tenantIds);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return searchResponse
        .aggregations()
        .get(VERSION_AGGREGATION)
        .sterms()
        .buckets()
        .array()
        .stream()
        .map(
            versionBucket -> {
              final String version = versionBucket.key().stringValue();
              final StringTermsAggregate versionTags =
                  versionBucket.aggregations().get(VERSION_TAG_AGGREGATION).sterms();
              final String versionTag =
                  versionTags.buckets().array().stream()
                      .findFirst()
                      .map(b -> b.key().stringValue())
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
    final BoolQuery.Builder rootQuery = new BoolQuery.Builder();
    rootQuery.must(
        m -> {
          if (fullyImported) {
            return m.exists(e -> e.field(xmlField));
          } else {
            return m.matchAll(a -> a);
          }
        });

    rootQuery.must(m -> m.matchAll(a -> a));
    if (!includeDeleted) {
      rootQuery.must(m -> m.term(t -> t.field(DEFINITION_DELETED).value(false)));
    }
    if (!definitionKeys.isEmpty()) {
      rootQuery.must(
          m ->
              m.terms(
                  t ->
                      t.field(resolveDefinitionKeyFieldFromType(type))
                          .terms(
                              tt ->
                                  tt.value(definitionKeys.stream().map(FieldValue::of).toList()))));
    }
    return getDefinitions(type, rootQuery, withXml);
  }

  public <T extends DefinitionOptimizeResponseDto> List<T> getDefinitions(
      final DefinitionType type, final BoolQuery.Builder filteredQuery, final boolean withXml) {
    final String xmlField = resolveXmlFieldFromType(type);
    final List<String> fieldsToExclude = withXml ? null : List.of(xmlField);
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s -> {
              s.optimizeIndex(esClient, resolveIndexNameForType(type))
                  .query(Query.of(q -> q.bool(filteredQuery.build())))
                  .size(LIST_FETCH_LIMIT)
                  .scroll(
                      Time.of(
                          t ->
                              t.time(
                                  configurationService
                                          .getElasticSearchConfiguration()
                                          .getScrollTimeoutInSeconds()
                                      + "s")));
              if (fieldsToExclude != null) {
                s.source(so -> so.filter(f -> f.excludes(fieldsToExclude)));
              }
              return s;
            });

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final SearchResponse<T> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, typeClass);
    } catch (final IOException e) {
      final String errorMsg =
          String.format("Was not able to retrieve definitions of type %s", type);
      LOG.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }

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
      final BoolQuery.Builder filterQuery) {
    // If no versions were given or if 'all' is among the versions, then no filtering is needed
    if (!CollectionUtils.isEmpty(versions)
        && !DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(versions)) {
      filterQuery.filter(
          f ->
              f.terms(
                  t ->
                      t.field(DEFINITION_VERSION)
                          .terms(
                              tt ->
                                  tt.value(
                                      versions.stream()
                                          .map(
                                              version ->
                                                  FieldValue.of(
                                                      convertToLatestParticularVersion(
                                                          version, latestVersionSupplier)))
                                          .collect(Collectors.toList())))));
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
    final BoolQuery.Builder builder = new BoolQuery.Builder();
    builder
        .must(
            m -> m.term(t -> t.field(resolveDefinitionKeyFieldFromType(type)).value(definitionKey)))
        .must(m -> m.term(t -> t.field(resolveVersionFieldFromType(type)).value(validVersion)))
        .must(m -> m.term(t -> t.field(DEFINITION_DELETED).value(false)))
        .must(m -> m.exists(e -> e.field(resolveXmlFieldFromType(type))));

    if (tenantId != null) {
      builder.must(m -> m.term(t -> t.field(TENANT_ID).value(tenantId)));
    } else {
      builder.mustNot(m -> m.exists(e -> e.field(TENANT_ID)));
    }

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, resolveIndexNameForType(type))
                    .query(Query.of(q -> q.bool(builder.build())))
                    .size(1));

    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final SearchResponse<T> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, typeClass);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch [%s] definition with key [%s], version [%s] and tenantId [%s]",
              type, definitionKey, validVersion, tenantId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.hits().total().value() == 0L) {
      LOG.debug(
          "Could not find [{}] definition with key [{}], version [{}] and tenantId [{}]",
          type,
          definitionKey,
          validVersion,
          tenantId);
      return Optional.empty();
    }

    final T definitionOptimizeDto =
        ElasticsearchReaderUtil.mapHits(
                searchResponse.hits(),
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
    LOG.debug(
        "Fetching latest fully imported [{}] definitions for key [{}] on each tenant", type, key);

    final FiltersAggregation keyFilterAgg =
        FiltersAggregation.of(
            f ->
                f.filters(
                    ff ->
                        ff.array(
                            List.of(
                                Query.of(
                                    q ->
                                        q.bool(
                                            b ->
                                                b.must(
                                                        m ->
                                                            m.term(
                                                                t ->
                                                                    t.field(
                                                                            resolveDefinitionKeyFieldFromType(
                                                                                type))
                                                                        .value(key)))
                                                    .must(
                                                        m ->
                                                            m.term(
                                                                t ->
                                                                    t.field(DEFINITION_DELETED)
                                                                        .value(false)))
                                                    .must(
                                                        m ->
                                                            m.exists(
                                                                e ->
                                                                    e.field(
                                                                        resolveXmlFieldFromType(
                                                                            type))))))))));

    final TermsAggregation tenantsAggregation =
        TermsAggregation.of(
            t ->
                t.field(DEFINITION_TENANT_ID)
                    .size(LIST_FETCH_LIMIT)
                    .missing(TENANT_NOT_DEFINED_VALUE));

    final Script numericVersionScript =
        createDefaultScript(
            "Integer.parseInt(doc['" + resolveVersionFieldFromType(type) + "'].value)");

    final Aggregation versionAggregation =
        Aggregation.of(
            a ->
                a.terms(
                        t ->
                            t.field(DEFINITION_VERSION)
                                .size(1) // only return bucket for latest version
                                .order(List.of(NamedValue.of("versionForSorting", SortOrder.Desc))))
                    // custom sort agg to sort by numeric version value (instead of string bucket
                    // key)
                    .aggregations(
                        "versionForSorting",
                        Aggregation.of(a1 -> a1.min(m -> m.script(numericVersionScript))))
                    .aggregations(
                        // return top hit in latest version bucket, should only be one
                        TOP_HITS_AGGREGATION, Aggregation.of(a2 -> a2.topHits(t -> t.size(1)))));

    final Aggregation definitionAgg =
        Aggregation.of(
            a ->
                a.filters(keyFilterAgg)
                    .aggregations(
                        TENANT_AGGREGATION,
                        Aggregation.of(
                            a1 ->
                                a1.terms(tenantsAggregation)
                                    .aggregations(VERSION_AGGREGATION, versionAggregation))));

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, resolveIndexNameForType(type))
                    .aggregations(DEFINITION_KEY_FILTER_AGGREGATION, definitionAgg)
                    .size(0));

    final Class<T> typeClass = resolveDefinitionClassFromType(type);

    final SearchResponse<T> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, typeClass);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch latest [%s] definitions for key [%s]", type, key);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final List<T> result = retrieveResultsFromLatestDefinitionPerTenant(type, searchResponse);

    if (result.isEmpty()) {
      LOG.debug("Could not find latest [{}] definitions with key [{}]", type, key);
    }

    return result;
  }

  private List<DefinitionWithTenantIdsDto> getDefinitionWithTenantIdsDtos(
      final Query.Builder filterQuery, final DefinitionType type) {
    // 2.1 group by tenant
    final TermsAggregation tenantsAggregation =
        TermsAggregation.of(
            b ->
                b.field(DEFINITION_TENANT_ID)
                    .size(LIST_FETCH_LIMIT)
                    .missing(TENANT_NOT_DEFINED_VALUE)
                    .order(List.of(NamedValue.of("_key", SortOrder.Asc))));
    // 2.2 group by name (should only be one)
    final Aggregation nameAggregation =
        Aggregation.of(
            a ->
                a.terms(
                        TermsAggregation.of(
                            b ->
                                b.field(DEFINITION_NAME)
                                    .size(1)
                                    .order(NamedValue.of("versionForSorting", SortOrder.Desc))))
                    .aggregations(
                        "versionForSorting",
                        Aggregation.of(
                            a1 ->
                                a1.min(
                                    m ->
                                        m.script(
                                            createDefaultScript(
                                                "Integer.parseInt(doc['version'].value)"))))));

    // 2.3 group by engine
    final Aggregation enginesAggregation =
        Aggregation.of(
            a ->
                a.terms(
                    t ->
                        t.field(DATA_SOURCE + "." + DataSourceDto.Fields.name)
                            .minDocCount(1)
                            .size(LIST_FETCH_LIMIT)));
    // 1. group by key and type
    final List<Map<String, CompositeAggregationSource>> keyAndTypeSources = new ArrayList<>();
    keyAndTypeSources.add(
        Map.of(
            DEFINITION_KEY_AGGREGATION,
            CompositeAggregationSource.of(
                c ->
                    c.terms(
                        t -> t.field(DEFINITION_KEY).missingBucket(false).order(SortOrder.Asc)))));
    keyAndTypeSources.add(
        Map.of(
            DEFINITION_TYPE_AGGREGATION,
            CompositeAggregationSource.of(
                c ->
                    c.terms(
                        t ->
                            t.field(DatabaseConstants.INDEX)
                                .missingBucket(false)
                                .order(SortOrder.Asc)))));

    final Supplier<CompositeAggregation.Builder> compositeSupplier =
        () -> {
          final CompositeAggregation.Builder builder = new CompositeAggregation.Builder();
          builder
              .sources(keyAndTypeSources)
              .size(
                  configurationService.getElasticSearchConfiguration().getAggregationBucketLimit());
          return builder;
        };

    final Function<CompositeAggregation.Builder, Aggregation.Builder.ContainerBuilder>
        keyAndTypeAggregation =
            (b) -> {
              final Aggregation.Builder builder = new Aggregation.Builder();
              return builder
                  .composite(b.build())
                  .aggregations(
                      TENANT_AGGREGATION, Aggregation.of(a1 -> a1.terms(tenantsAggregation)))
                  .aggregations(NAME_AGGREGATION, nameAggregation)
                  .aggregations(ENGINE_AGGREGATION, enginesAggregation);
            };

    final List<CompositeBucket> keyAndTypeAggBuckets =
        performSearchAndCollectAllKeyAndTypeBuckets(
            filterQuery, resolveIndexNameForType(type), keyAndTypeAggregation, compositeSupplier);

    return keyAndTypeAggBuckets.stream()
        .map(
            keyAndTypeAgg -> {
              final String indexAliasName =
                  keyAndTypeAgg.key().get(DEFINITION_TYPE_AGGREGATION).stringValue();
              final String definitionKey =
                  keyAndTypeAgg.key().get(DEFINITION_KEY_AGGREGATION).stringValue();
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
                      .map(b -> b.key().stringValue())
                      .orElse(null),
                  resolveDefinitionTypeFromIndexAlias(indexAliasName),
                  tenantResult.buckets().array().stream()
                      .map(b -> b.key().stringValue())
                      // convert null bucket back to a `null` id
                      .map(
                          tenantId ->
                              TENANT_NOT_DEFINED_VALUE.equalsIgnoreCase(tenantId) ? null : tenantId)
                      .collect(Collectors.toList()),
                  enginesResult.buckets().array().stream()
                      .map(b -> b.key().stringValue())
                      .collect(Collectors.toSet()));
            })
        .toList();
  }

  private void addTenantIdFilter(final Set<String> tenantIds, final BoolQuery.Builder query) {
    if (!CollectionUtils.isEmpty(tenantIds)) {
      final BoolQuery boolQuery =
          BoolQuery.of(
              b -> {
                b.minimumShouldMatch("1");
                if (tenantIds.contains(null)) {
                  b.should(s -> s.bool(bb -> bb.mustNot(m -> m.exists(e -> e.field(TENANT_ID)))));
                }
                final Set<String> nonNullValues =
                    tenantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
                if (!nonNullValues.isEmpty()) {
                  b.should(
                      s ->
                          s.terms(
                              t ->
                                  t.field(TENANT_ID)
                                      .terms(
                                          tt ->
                                              tt.value(
                                                  nonNullValues.stream()
                                                      .map(FieldValue::of)
                                                      .toList()))));
                }
                return b;
              });

      query.filter(Query.of(q -> q.bool(boolQuery)));
    }
  }

  private List<CompositeBucket> performSearchAndCollectAllKeyAndTypeBuckets(
      final Query.Builder filterQuery,
      final String[] definitionIndexNames,
      final Function<CompositeAggregation.Builder, Aggregation.Builder.ContainerBuilder>
          keyAggregation,
      final Supplier<CompositeAggregation.Builder> supplier) {
    final Aggregation.Builder.ContainerBuilder build = keyAggregation.apply(supplier.get());
    final Query query = filterQuery.build();
    SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, definitionIndexNames)
                    .query(query)
                    .size(0)
                    .aggregations(DEFINITION_KEY_AND_TYPE_AGGREGATION, a -> build));

    final List<CompositeBucket> keyAndTypeAggBuckets = new ArrayList<>();
    try {
      SearchResponse<?> searchResponse = esClient.search(searchRequest, Object.class);
      CompositeAggregate keyAndTypeAggregationResult =
          searchResponse.aggregations().get(DEFINITION_KEY_AND_TYPE_AGGREGATION).composite();
      while (!keyAndTypeAggregationResult.buckets().array().isEmpty()) {
        keyAndTypeAggBuckets.addAll(keyAndTypeAggregationResult.buckets().array());

        final CompositeAggregation.Builder after =
            supplier.get().after(keyAndTypeAggregationResult.afterKey());
        searchRequest =
            OptimizeSearchRequestBuilderES.of(
                s ->
                    s.optimizeIndex(esClient, definitionIndexNames)
                        .query(query)
                        .size(0)
                        .aggregations(
                            DEFINITION_KEY_AND_TYPE_AGGREGATION,
                            keyAggregation.apply(after).build()));
        searchResponse = esClient.search(searchRequest, Object.class);
        keyAndTypeAggregationResult =
            searchResponse.aggregations().get(DEFINITION_KEY_AND_TYPE_AGGREGATION).composite();
      }
    } catch (final IOException e) {
      final String reason = "Was not able to fetch definitions.";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return keyAndTypeAggBuckets;
  }

  private <T extends DefinitionOptimizeResponseDto>
      List<T> retrieveResultsFromLatestDefinitionPerTenant(
          final DefinitionType type, final SearchResponse<T> searchResponse) {
    final Class<T> typeClass = resolveDefinitionClassFromType(type);
    final List<T> results = new ArrayList<>();
    final FiltersAggregate filteredDefsAgg =
        searchResponse.aggregations().get(DEFINITION_KEY_FILTER_AGGREGATION).filters();
    final StringTermsAggregate tenantsAgg =
        filteredDefsAgg.buckets().array().get(0).aggregations().get(TENANT_AGGREGATION).sterms();

    // There should be max. one version bucket in each tenant bucket containing the latest
    // definition for this tenant
    for (final StringTermsBucket tenantBucket : tenantsAgg.buckets().array()) {
      final StringTermsAggregate versionsAgg =
          tenantBucket.aggregations().get(VERSION_AGGREGATION).sterms();
      for (final StringTermsBucket b : versionsAgg.buckets().array()) {
        final TopHitsAggregate topHits = b.aggregations().get(TOP_HITS_AGGREGATION).topHits();
        final List<Hit<Object>> list =
            topHits.hits().hits().stream()
                .map(r -> Hit.of(h -> h.id(r.id()).source(r.source()).index(r.index())))
                .toList();
        results.addAll(
            ElasticsearchReaderUtil.mapHits(
                HitsMetadata.of(h -> h.hits(list)),
                1,
                typeClass,
                createMappingFunctionForDefinitionType(typeClass)));
      }
    }
    return results;
  }

  private <T extends DefinitionOptimizeResponseDto>
      Function<Hit<?>, T> createMappingFunctionForDefinitionType(final Class<T> type) {
    return hit -> {
      T definitionDto = null;
      if (type.isInstance(hit.source())) {
        definitionDto = (T) hit.source();
      } else {
        definitionDto =
            objectMapper.convertValue(((JsonData) hit.source()).to(List.class).get(0), type);
      }
      if (ProcessDefinitionOptimizeDto.class.equals(type)) {
        final ProcessDefinitionOptimizeDto processDefinition =
            (ProcessDefinitionOptimizeDto) definitionDto;
        processDefinition.setType(DefinitionType.PROCESS);
      } else {
        definitionDto.setType(DefinitionType.DECISION);
      }
      return definitionDto;
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
