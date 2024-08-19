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
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
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
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class EntitiesReaderES implements EntitiesReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final LocalizationService localizationService;
  private final ObjectMapper objectMapper;

  @Override
  public List<CollectionEntity> getAllPrivateEntities() {
    return getAllPrivateEntitiesForOwnerId(null);
  }

  @Override
  public List<CollectionEntity> getAllPrivateEntitiesForOwnerId(final String ownerId) {
    log.debug("Fetching all available entities for user [{}]", ownerId);

    final BoolQueryBuilder query =
        boolQuery()
            .mustNot(existsQuery(COLLECTION_ID))
            .must(
                boolQuery()
                    .minimumShouldMatch(1)
                    .should(termQuery(MANAGEMENT_DASHBOARD, false))
                    .should(termQuery(DATA + "." + MANAGEMENT_REPORT, false))
                    .should(
                        boolQuery()
                            .mustNot(existsQuery(MANAGEMENT_DASHBOARD))
                            .mustNot(existsQuery(DATA + "." + MANAGEMENT_REPORT))))
            .must(
                boolQuery()
                    .minimumShouldMatch(1)
                    .should(termQuery(INSTANT_PREVIEW_DASHBOARD, false))
                    .should(termQuery(DATA + "." + INSTANT_PREVIEW_REPORT, false))
                    .should(
                        boolQuery()
                            .mustNot(existsQuery(INSTANT_PREVIEW_DASHBOARD))
                            .mustNot(existsQuery(DATA + "." + INSTANT_PREVIEW_REPORT))));

    if (ownerId != null) {
      query.must(termQuery(OWNER, ownerId));
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .size(LIST_FETCH_LIMIT)
            .fetchSource(null, ENTITY_LIST_EXCLUDES);
    final SearchRequest searchRequest =
        createReportAndDashboardSearchRequest()
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
      log.error("Was not able to retrieve private entities!", e);
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
    log.debug(
        "Counting all available entities for collection ids [{}]",
        collections.stream().map(BaseCollectionDefinitionDto::getId).toList());

    if (collections.isEmpty()) {
      return new HashMap<>();
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                termsQuery(
                    COLLECTION_ID,
                    collections.stream().map(BaseCollectionDefinitionDto::getId).toList()))
            .size(0);

    collections.forEach(
        collection -> {
          final String collectionId = collection.getId();
          final FilterAggregationBuilder byCollectionIdFilterAggregation =
              filter(collectionId, boolQuery().filter(termQuery(COLLECTION_ID, collectionId)));
          searchSourceBuilder.aggregation(byCollectionIdFilterAggregation);
          final TermsAggregationBuilder byIndexNameAggregation =
              terms(AGG_BY_INDEX_COUNT).field("_index");
          byCollectionIdFilterAggregation.subAggregation(byIndexNameAggregation);
        });

    final SearchRequest searchRequest =
        createReportAndDashboardSearchRequest().source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return searchResponse.getAggregations().asList().stream()
          .map(Filter.class::cast)
          .map(
              collectionFilterAggregation ->
                  new AbstractMap.SimpleEntry<>(
                      collectionFilterAggregation.getName(),
                      extractEntityIndexCounts(collectionFilterAggregation)))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to count collection entities!", e);
    }
  }

  @Override
  public List<CollectionEntity> getAllEntitiesForCollection(final String collectionId) {
    log.debug("Fetching all available entities for collection [{}]", collectionId);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(termQuery(COLLECTION_ID, collectionId))
            .size(LIST_FETCH_LIMIT);
    return runEntitiesSearchRequest(searchSourceBuilder);
  }

  @Override
  public Optional<EntityNameResponseDto> getEntityNames(
      final EntityNameRequestDto requestDto, final String locale) {
    log.debug(
        String.format("Performing get entity names search request %s", requestDto.toString()));
    final MultiGetResponse multiGetItemResponse = runGetEntityNamesRequest(requestDto);

    if (!atLeastOneResponseExistsForMultiGet(multiGetItemResponse)) {
      return Optional.empty();
    }

    final EntityNameResponseDto result = new EntityNameResponseDto();
    for (final MultiGetItemResponse itemResponse : multiGetItemResponse) {
      final GetResponse response = itemResponse.getResponse();
      if (response.isExists()) {
        final String entityId = response.getId();
        final CollectionEntity entity = readCollectionEntity(response, entityId);
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

    return Optional.ofNullable(result);
  }

  private Map<EntityType, Long> extractEntityIndexCounts(final Filter collectionFilterAggregation) {
    final Terms byIndexNameTerms =
        collectionFilterAggregation.getAggregations().get(AGG_BY_INDEX_COUNT);
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
      final Terms byIndexNameTerms, final IndexMappingCreator<?> indexMapper) {
    if (indexMapper.isCreateFromTemplate()) {
      throw new OptimizeRuntimeException(
          "Cannot fetch the document count for indices created from template");
    }
    return Optional.ofNullable(
            byIndexNameTerms.getBucketByKey(
                optimizeIndexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(indexMapper)))
        .map(MultiBucketsAggregation.Bucket::getDocCount)
        .orElse(0L);
  }

  private CollectionEntity readCollectionEntity(final GetResponse response, final String entityId) {
    final CollectionEntity entity;
    try {
      entity = objectMapper.readValue(response.getSourceAsString(), CollectionEntity.class);
    } catch (final IOException e) {
      final String reason = String.format("Can't read collection entity with id [%s].", entityId);
      throw new OptimizeRuntimeException(reason, e);
    }
    return entity;
  }

  private MultiGetResponse runGetEntityNamesRequest(final EntityNameRequestDto requestDto) {
    final MultiGetRequest request = new MultiGetRequest();
    addGetEntityToRequest(request, requestDto.getReportId(), SINGLE_PROCESS_REPORT_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getReportId(), SINGLE_DECISION_REPORT_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getReportId(), COMBINED_REPORT_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getDashboardId(), DASHBOARD_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getCollectionId(), COLLECTION_INDEX_NAME);
    if (request.getItems().isEmpty()) {
      throw new BadRequestException("No ids for entity name request provided");
    }

    final MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not get entity names search request %s", requestDto);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  private void addGetEntityToRequest(
      final MultiGetRequest request, final String entityId, final String entityIndexName) {
    if (entityId != null) {
      request.add(new MultiGetRequest.Item(entityIndexName, entityId));
    }
  }

  private List<CollectionEntity> runEntitiesSearchRequest(
      final SearchSourceBuilder searchSourceBuilder) {
    final SearchRequest searchRequest =
        createReportAndDashboardSearchRequest()
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
      log.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        CollectionEntity.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  private SearchRequest createReportAndDashboardSearchRequest() {
    return new SearchRequest(
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME,
        COMBINED_REPORT_INDEX_NAME,
        DASHBOARD_INDEX_NAME);
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
