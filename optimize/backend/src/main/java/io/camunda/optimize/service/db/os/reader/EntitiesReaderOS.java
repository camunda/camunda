/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.terms;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.INSTANT_PREVIEW_DASHBOARD;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.OWNER;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.INSTANT_PREVIEW_REPORT;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.schema.index.DashboardIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.CombinedReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleDecisionReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import io.camunda.optimize.service.db.reader.EntitiesReader;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketBase;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.mget.MultiGetResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EntitiesReaderOS implements EntitiesReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final LocalizationService localizationService;

  @Override
  public List<CollectionEntity> getAllPrivateEntities() {
    return getAllPrivateEntitiesForOwnerId(null);
  }

  @Override
  public List<CollectionEntity> getAllPrivateEntitiesForOwnerId(final String ownerId) {
    log.debug("Fetching all available entities for user [{}]", ownerId);

    final BoolQuery.Builder query =
        new BoolQuery.Builder()
            .mustNot(QueryDSL.exists(COLLECTION_ID))
            .must(
                new BoolQuery.Builder()
                    .minimumShouldMatch("1")
                    .should(term(MANAGEMENT_DASHBOARD, false))
                    .should(term(DATA + "." + MANAGEMENT_REPORT, false))
                    .should(
                        new BoolQuery.Builder()
                            .mustNot(QueryDSL.exists(MANAGEMENT_DASHBOARD))
                            .mustNot(QueryDSL.exists(DATA + "." + MANAGEMENT_REPORT))
                            .build()
                            .toQuery())
                    .build()
                    .toQuery())
            .must(
                new BoolQuery.Builder()
                    .minimumShouldMatch("1")
                    .should(term(INSTANT_PREVIEW_DASHBOARD, false))
                    .should(term(DATA + "." + INSTANT_PREVIEW_REPORT, false))
                    .should(
                        new BoolQuery.Builder()
                            .mustNot(QueryDSL.exists(INSTANT_PREVIEW_DASHBOARD))
                            .mustNot(QueryDSL.exists(DATA + "." + INSTANT_PREVIEW_REPORT))
                            .build()
                            .toQuery())
                    .build()
                    .toQuery());

    if (ownerId != null) {
      query.must(term(OWNER, ownerId));
    }

    final SourceFilter filter =
        new SourceFilter.Builder().excludes(Arrays.asList(ENTITY_LIST_EXCLUDES)).build();

    final SourceConfig sourceConfig = new SourceConfig.Builder().filter(filter).build();

    final SearchRequest.Builder requestBuilder =
        createReportAndDashboardSearchRequest()
            .size(LIST_FETCH_LIMIT)
            .query(query.build().toQuery())
            .source(sourceConfig)
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    final OpenSearchDocumentOperations.AggregatedResult<Hit<CollectionEntity>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(requestBuilder, CollectionEntity.class);
    } catch (final IOException e) {
      log.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }

  @Override
  public Map<String, Map<EntityType, Long>> countEntitiesForCollections(
      final List<? extends BaseCollectionDefinitionDto<?>> collections) {
    final List<String> collectionIds =
        collections.stream().map(BaseCollectionDefinitionDto::getId).toList();
    log.debug("Counting all available entities for collection ids [{}]", collectionIds);

    if (collections.isEmpty()) {
      return new HashMap<>();
    }

    final Map<String, Aggregation> aggregations =
        collectionIds.stream()
            .map(
                collectionId -> {
                  final Aggregation termsAggregation =
                      new TermsAggregation.Builder().field(INDEX_FIELD).build()._toAggregation();
                  final Aggregation aggregation =
                      new Aggregation.Builder()
                          .filter(term(COLLECTION_ID, collectionId))
                          .aggregations(Map.of(AGG_BY_INDEX_COUNT, termsAggregation))
                          .build();
                  return Pair.of(collectionId, aggregation);
                })
            .collect(MapUtil.pairCollector());

    final SearchRequest.Builder searchRequestBuilder =
        createReportAndDashboardSearchRequest()
            .query(terms(COLLECTION_ID, collectionIds))
            .aggregations(aggregations)
            .size(0);

    final String errorMessage = "Was not able to count collection entities!";
    final SearchResponse<CollectionEntity> searchResponse =
        osClient.search(searchRequestBuilder, CollectionEntity.class, errorMessage);
    return searchResponse.aggregations().entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), extractEntityIndexCounts(entry.getValue().filter())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public List<CollectionEntity> getAllEntitiesForCollection(final String collectionId) {
    log.debug("Fetching all available entities for collection [{}]", collectionId);
    final SearchRequest.Builder requestBuilder =
        createReportAndDashboardSearchRequest()
            .size(LIST_FETCH_LIMIT)
            .query(term(COLLECTION_ID, collectionId))
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    final OpenSearchDocumentOperations.AggregatedResult<Hit<CollectionEntity>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(requestBuilder, CollectionEntity.class);
    } catch (final IOException e) {
      log.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }

  @Override
  public Optional<EntityNameResponseDto> getEntityNames(
      final EntityNameRequestDto requestDto, final String locale) {
    log.debug(
        String.format("Performing get entity names search request %s", requestDto.toString()));
    final MgetResponse<CollectionEntity> multiGetItemResponse =
        runGetEntityNamesRequest(requestDto);

    final boolean atLeastOneResponseExistsForMultiGet =
        multiGetItemResponse.docs().stream().anyMatch(doc -> doc.result().found());
    if (!atLeastOneResponseExistsForMultiGet) {
      return Optional.empty();
    }

    final EntityNameResponseDto result = new EntityNameResponseDto();
    for (final MultiGetResponseItem<CollectionEntity> itemResponse : multiGetItemResponse.docs()) {
      if (itemResponse.isResult()) {
        final Optional<CollectionEntity> optionalResponse =
            OpensearchReaderUtil.processGetResponse(itemResponse.result());

        optionalResponse.ifPresent(
            entity -> {
              final String entityId = entity.getId();
              if (entityId.equals(requestDto.getCollectionId())) {
                result.setCollectionName(entity.getName());
              }
              if (entityId.equals(requestDto.getDashboardId())) {
                result.setDashboardName(
                    getLocalizedDashboardName((DashboardDefinitionRestDto) entity, locale));
              } else if (entityId.equals(requestDto.getReportId())) {
                result.setReportName(getLocalizedReportName(localizationService, entity, locale));
              }
            });
      }
    }
    return Optional.of(result);
  }

  private Map<EntityType, Long> extractEntityIndexCounts(
      final FilterAggregate collectionFilterAggregation) {
    final StringTermsAggregate byIndexNameTerms =
        collectionFilterAggregation.aggregations().get(AGG_BY_INDEX_COUNT).sterms();
    final long singleProcessReportCount =
        getDocCountForIndex(byIndexNameTerms, new SingleProcessReportIndexOS());
    final long combinedProcessReportCount =
        getDocCountForIndex(byIndexNameTerms, new CombinedReportIndexOS());
    final long singleDecisionReportCount =
        getDocCountForIndex(byIndexNameTerms, new SingleDecisionReportIndexOS());
    final long dashboardCount = getDocCountForIndex(byIndexNameTerms, new DashboardIndexOS());
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
                    .equals(s.key()))
        .findFirst()
        .map(MultiBucketBase::docCount)
        .orElse(0L);
  }

  private MgetResponse<CollectionEntity> runGetEntityNamesRequest(
      final EntityNameRequestDto requestDto) {
    final Map<String, String> indexesToEntitiesId = new HashMap<>();
    indexesToEntitiesId.put(SINGLE_PROCESS_REPORT_INDEX_NAME, requestDto.getReportId());
    indexesToEntitiesId.put(SINGLE_DECISION_REPORT_INDEX_NAME, requestDto.getReportId());
    indexesToEntitiesId.put(COMBINED_REPORT_INDEX_NAME, requestDto.getReportId());
    indexesToEntitiesId.put(DASHBOARD_INDEX_NAME, requestDto.getDashboardId());
    indexesToEntitiesId.put(COLLECTION_INDEX_NAME, requestDto.getCollectionId());

    final boolean isEmpty =
        indexesToEntitiesId.entrySet().stream().noneMatch(e -> e.getValue() != null);

    if (isEmpty) {
      throw new BadRequestException("No ids for entity name request provided");
    }

    final String errorMessage =
        String.format("Could not get entity names search request %s", requestDto);
    return osClient.mget(CollectionEntity.class, errorMessage, indexesToEntitiesId);
  }

  private SearchRequest.Builder createReportAndDashboardSearchRequest() {
    return new SearchRequest.Builder()
        .index(
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
