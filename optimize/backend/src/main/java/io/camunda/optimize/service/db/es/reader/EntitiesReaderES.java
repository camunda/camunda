/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil.atLeastOneResponseExistsForMultiGet;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.INSTANT_PREVIEW_DASHBOARD;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.OWNER;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.INSTANT_PREVIEW_REPORT;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.get.GetResult;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeMultiGetOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.schema.index.DashboardIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.CombinedReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleDecisionReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import io.camunda.optimize.service.db.reader.EntitiesReader;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class EntitiesReaderES implements EntitiesReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(EntitiesReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final LocalizationService localizationService;
  private final ObjectMapper objectMapper;

  public EntitiesReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final LocalizationService localizationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.optimizeIndexNameService = optimizeIndexNameService;
    this.localizationService = localizationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<CollectionEntity> getAllPrivateEntities() {
    return getAllPrivateEntitiesForOwnerId(null);
  }

  @Override
  public List<CollectionEntity> getAllPrivateEntitiesForOwnerId(final String ownerId) {
    LOG.debug("Fetching all available entities for user [{}]", ownerId);

    final Query query =
        Query.of(
            q ->
                q.bool(
                    b -> {
                      b.mustNot(m -> m.exists(e -> e.field(COLLECTION_ID)))
                          .must(
                              m ->
                                  m.bool(
                                      bb ->
                                          bb.minimumShouldMatch("1")
                                              .should(
                                                  s ->
                                                      s.term(
                                                          t ->
                                                              t.field(MANAGEMENT_DASHBOARD)
                                                                  .value(false)))
                                              .should(
                                                  s ->
                                                      s.term(
                                                          t ->
                                                              t.field(
                                                                      DATA
                                                                          + "."
                                                                          + MANAGEMENT_REPORT)
                                                                  .value(false)))
                                              .should(
                                                  s ->
                                                      s.bool(
                                                          bbb ->
                                                              bbb.mustNot(
                                                                      mm ->
                                                                          mm.exists(
                                                                              e ->
                                                                                  e.field(
                                                                                      MANAGEMENT_DASHBOARD)))
                                                                  .mustNot(
                                                                      mm ->
                                                                          mm.exists(
                                                                              e ->
                                                                                  e.field(
                                                                                      DATA
                                                                                          + "."
                                                                                          + MANAGEMENT_REPORT)))))))
                          .must(
                              m ->
                                  m.bool(
                                      bb ->
                                          bb.minimumShouldMatch("1")
                                              .should(
                                                  s ->
                                                      s.term(
                                                          t ->
                                                              t.field(INSTANT_PREVIEW_DASHBOARD)
                                                                  .value(false)))
                                              .should(
                                                  s ->
                                                      s.term(
                                                          t ->
                                                              t.field(
                                                                      DATA
                                                                          + "."
                                                                          + INSTANT_PREVIEW_REPORT)
                                                                  .value(false)))
                                              .should(
                                                  s ->
                                                      s.bool(
                                                          bbb ->
                                                              bbb.mustNot(
                                                                      mm ->
                                                                          mm.exists(
                                                                              e ->
                                                                                  e.field(
                                                                                      INSTANT_PREVIEW_DASHBOARD)))
                                                                  .mustNot(
                                                                      mm ->
                                                                          mm.exists(
                                                                              e ->
                                                                                  e.field(
                                                                                      DATA
                                                                                          + "."
                                                                                          + INSTANT_PREVIEW_REPORT)))))));
                      if (ownerId != null) {
                        b.must(m -> m.term(t -> t.field(OWNER).value(ownerId)));
                      }
                      return b;
                    }));

    final SearchRequest searchRequest =
        createReportAndDashboardSearchRequest()
            .query(query)
            .size(LIST_FETCH_LIMIT)
            .source(s -> s.filter(f -> f.excludes(List.of(ENTITY_LIST_EXCLUDES))))
            .scroll(
                t ->
                    t.time(
                        configurationService
                                .getElasticSearchConfiguration()
                                .getScrollTimeoutInSeconds()
                            + "s"))
            .build();

    final SearchResponse<CollectionEntity> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, CollectionEntity.class);
    } catch (final IOException e) {
      LOG.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        CollectionEntity.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public Map<String, Map<EntityType, Long>> countEntitiesForCollections(
      final List<? extends BaseCollectionDefinitionDto<?>> collections) {
    LOG.debug(
        "Counting all available entities for collection ids [{}]",
        collections.stream().map(BaseCollectionDefinitionDto::getId).toList());

    if (collections.isEmpty()) {
      return new HashMap<>();
    }

    final SearchRequest.Builder searchRequestBuilder =
        createReportAndDashboardSearchRequest()
            .query(
                q ->
                    q.terms(
                        t ->
                            t.field(COLLECTION_ID)
                                .terms(
                                    tt ->
                                        tt.value(
                                            collections.stream()
                                                .map(BaseCollectionDefinitionDto::getId)
                                                .map(FieldValue::of)
                                                .toList()))))
            .size(0);

    collections.forEach(
        collection -> {
          final String collectionId = collection.getId();
          final Aggregation aggregation =
              Aggregation.of(
                  a ->
                      a.filter(
                              f ->
                                  f.bool(
                                      b ->
                                          b.filter(
                                              ff ->
                                                  ff.term(
                                                      t ->
                                                          t.field(COLLECTION_ID)
                                                              .value(collectionId)))))
                          .aggregations(
                              AGG_BY_INDEX_COUNT,
                              Aggregation.of(aa -> aa.terms(t -> t.field(INDEX_FIELD)))));
          searchRequestBuilder.aggregations(collectionId, aggregation);
        });

    try {
      final SearchResponse<?> searchResponse =
          esClient.search(searchRequestBuilder.build(), Object.class);
      return searchResponse.aggregations().entrySet().stream()
          .map(
              (entry) ->
                  new AbstractMap.SimpleEntry<>(
                      entry.getKey(), extractEntityIndexCounts(entry.getValue().filter())))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to count collection entities!", e);
    }
  }

  @Override
  public List<CollectionEntity> getAllEntitiesForCollection(final String collectionId) {
    LOG.debug("Fetching all available entities for collection [{}]", collectionId);

    final SearchRequest searchRequest =
        createReportAndDashboardSearchRequest()
            .query(q -> q.term(t -> t.field(COLLECTION_ID).value(collectionId)))
            .size(LIST_FETCH_LIMIT)
            .scroll(
                t ->
                    t.time(
                        configurationService
                                .getElasticSearchConfiguration()
                                .getScrollTimeoutInSeconds()
                            + "s"))
            .build();

    final SearchResponse<CollectionEntity> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, CollectionEntity.class);
    } catch (final IOException e) {
      LOG.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        CollectionEntity.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public Optional<EntityNameResponseDto> getEntityNames(
      final EntityNameRequestDto requestDto, final String locale) {
    LOG.debug(
        String.format("Performing get entity names search request %s", requestDto.toString()));
    final MgetResponse<CollectionEntity> multiGetItemResponse =
        runGetEntityNamesRequest(requestDto, CollectionEntity.class);

    if (!atLeastOneResponseExistsForMultiGet(multiGetItemResponse)) {
      return Optional.empty();
    }

    final EntityNameResponseDto result = new EntityNameResponseDto();
    for (final MultiGetResponseItem<CollectionEntity> itemResponse : multiGetItemResponse.docs()) {
      final GetResult<CollectionEntity> response = itemResponse.result();
      if (response.found()) {
        final String entityId = response.id();
        final CollectionEntity entity = response.source();
        if (entityId.equals(requestDto.getCollectionId())) {
          result.setCollectionName(entity.getName());
        }

        if (entityId.equals(requestDto.getDashboardId())) {
          result.setDashboardName(
              getLocalizedDashboardName((DashboardDefinitionRestDto) entity, locale));
        } else if (entityId.equals(requestDto.getReportId())) {
          result.setReportName(getLocalizedReportName(localizationService, entity, locale));
        }
      }
    }

    return Optional.of(result);
  }

  private Map<EntityType, Long> extractEntityIndexCounts(
      final FilterAggregate collectionFilterAggregation) {
    final StringTermsAggregate byIndexNameTerms =
        collectionFilterAggregation.aggregations().get(AGG_BY_INDEX_COUNT).sterms();
    final long singleProcessReportCount =
        getDocCountForIndex(byIndexNameTerms, new SingleProcessReportIndexES());
    final long combinedProcessReportCount =
        getDocCountForIndex(byIndexNameTerms, new CombinedReportIndexES());
    final long singleDecisionReportCount =
        getDocCountForIndex(byIndexNameTerms, new SingleDecisionReportIndexES());
    final long dashboardCount = getDocCountForIndex(byIndexNameTerms, new DashboardIndexES());
    return ImmutableMap.of(
        EntityType.DASHBOARD,
        dashboardCount,
        EntityType.REPORT,
        singleProcessReportCount + singleDecisionReportCount + combinedProcessReportCount);
  }

  private long getDocCountForIndex(
      final StringTermsAggregate byIndexNameTerms, final IndexMappingCreator<?> indexMapper) {
    if (indexMapper.isCreateFromTemplate()) {
      throw new OptimizeRuntimeException(
          "Cannot fetch the document count for indices created from template");
    }
    return byIndexNameTerms.buckets().array().stream()
        .filter(
            s ->
                optimizeIndexNameService
                    .getOptimizeIndexNameWithVersionWithoutSuffix(indexMapper)
                    .equals(s.key().stringValue()))
        .findFirst()
        .map(MultiBucketBase::docCount)
        .orElse(0L);
  }

  private <T> MgetResponse<T> runGetEntityNamesRequest(
      final EntityNameRequestDto requestDto, final Class<T> clazz) {
    final MgetRequest.Builder builder = new MgetRequest.Builder();
    addGetEntityToRequest(builder, requestDto.getReportId(), SINGLE_PROCESS_REPORT_INDEX_NAME);
    addGetEntityToRequest(builder, requestDto.getReportId(), SINGLE_DECISION_REPORT_INDEX_NAME);
    addGetEntityToRequest(builder, requestDto.getReportId(), COMBINED_REPORT_INDEX_NAME);
    addGetEntityToRequest(builder, requestDto.getDashboardId(), DASHBOARD_INDEX_NAME);
    addGetEntityToRequest(builder, requestDto.getCollectionId(), COLLECTION_INDEX_NAME);
    final MgetRequest request = builder.build();
    if (request.docs().isEmpty()) {
      throw new BadRequestException("No ids for entity name request provided");
    }

    final MgetResponse<T> multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request, clazz);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not get entity names search request %s", requestDto);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  private void addGetEntityToRequest(
      final MgetRequest.Builder request, final String entityId, final String entityIndexName) {
    if (entityId != null) {
      final OptimizeMultiGetOperationBuilderES builder = new OptimizeMultiGetOperationBuilderES();
      request.docs(d -> builder.optimizeIndex(esClient, entityIndexName).id(entityId));
    }
  }

  private SearchRequest.Builder createReportAndDashboardSearchRequest() {
    final OptimizeSearchRequestBuilderES searchRequest = new OptimizeSearchRequestBuilderES();
    searchRequest.optimizeIndex(
        esClient,
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME,
        COMBINED_REPORT_INDEX_NAME,
        DASHBOARD_INDEX_NAME);
    return searchRequest;
  }

  private String getLocalizedDashboardName(
      final DashboardDefinitionRestDto dashboardEntity, final String locale) {
    if (dashboardEntity.isInstantPreviewDashboard()) {
      return localizationService.getLocalizationForInstantPreviewDashboardCode(
          locale, dashboardEntity.getName());
    } else if (dashboardEntity.isManagementDashboard()) {
      return localizationService.getLocalizationForManagementDashboardCode(
          locale, dashboardEntity.getName());
    }
    return dashboardEntity.getName();
  }
}
