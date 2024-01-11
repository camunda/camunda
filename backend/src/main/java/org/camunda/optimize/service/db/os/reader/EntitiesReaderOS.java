/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.AggregationDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.os.schema.index.DashboardIndexOS;
import org.camunda.optimize.service.db.os.schema.index.report.CombinedReportIndexOS;
import org.camunda.optimize.service.db.os.schema.index.report.SingleDecisionReportIndexOS;
import org.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import org.camunda.optimize.service.db.reader.EntitiesReader;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
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

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.DashboardIndex.INSTANT_PREVIEW_DASHBOARD;
import static org.camunda.optimize.service.db.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.OWNER;
import static org.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.INSTANT_PREVIEW_REPORT;
import static org.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

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

    final BoolQuery.Builder query = new BoolQuery.Builder()
      .mustNot(QueryDSL.exists(COLLECTION_ID))
      .must(new BoolQuery.Builder()
              .minimumShouldMatch("1")
              .should(QueryDSL.term(MANAGEMENT_DASHBOARD, false))
              .should(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, false))
              .should(new BoolQuery.Builder()
                        .mustNot(QueryDSL.exists(MANAGEMENT_DASHBOARD))
                        .mustNot(QueryDSL.exists(DATA + "." + MANAGEMENT_REPORT))
                        .build()._toQuery()).build()._toQuery())
      .must(new BoolQuery.Builder()
              .minimumShouldMatch("1")
              .should(QueryDSL.term(INSTANT_PREVIEW_DASHBOARD, false))
              .should(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, false))
              .should(new BoolQuery.Builder()
                        .mustNot(QueryDSL.exists(INSTANT_PREVIEW_DASHBOARD))
                        .mustNot(QueryDSL.exists(DATA + "." + INSTANT_PREVIEW_REPORT)).build()._toQuery())
              .build()
              ._toQuery()
      );

    if (ownerId != null) {
      query.must(QueryDSL.term(OWNER, ownerId));
    }

    SourceFilter filter = new SourceFilter.Builder()
      .excludes(Arrays.asList(ENTITY_LIST_EXCLUDES))
      .build();

    SourceConfig sourceConfig = new SourceConfig.Builder()
      .filter(filter)
      .build();

    SearchRequest.Builder requestBuilder = createReportAndDashboardSearchRequest()
      .size(LIST_FETCH_LIMIT)
      .query(query.build()._toQuery())
      .source(sourceConfig)
      .scroll(RequestDSL.time(String.valueOf(configurationService.getOpenSearchConfiguration().getScrollTimeoutInSeconds())));

    OpenSearchDocumentOperations.AggregatedResult<Hit<CollectionEntity>> scrollResp;
    try {
      scrollResp = osClient.scrollOs(requestBuilder, CollectionEntity.class);
    } catch (IOException e) {
      log.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }

  @Override
  public Map<String, Map<EntityType, Long>> countEntitiesForCollections(final List<? extends BaseCollectionDefinitionDto> collections) {
    List<String> collectionIds = collections.stream().map(BaseCollectionDefinitionDto::getId).toList();
    log.debug(
      "Counting all available entities for collection ids [{}]",
      collectionIds
    );

    if (collections.isEmpty()) {
      return new HashMap<>();
    }

    final SearchRequest.Builder searchRequestBuilder =
      createReportAndDashboardSearchRequest()
        .query(QueryDSL.terms(
          COLLECTION_ID,
          collectionIds,
          FieldValue::of
        ))
        .size(0);

    collectionIds.forEach(collectionId -> {
      final FiltersAggregation byCollectionIdFilterAggregation =
        AggregationDSL.filtersAggregation(Collections.singletonMap(collectionId, QueryDSL.term(COLLECTION_ID, collectionId)));

      //AGG_BY_INDEX_COUNT
      final TermsAggregation byIndexNameAggregation = TermsAggregation.of(a -> a.field("_index"));

      Aggregation collectionFilterAggregation = AggregationDSL.withSubaggregations(
        byIndexNameAggregation,
        Collections.singletonMap(
          "collectionFilter",
          new Aggregation.Builder().filters(byCollectionIdFilterAggregation).build()
        )
      );
      searchRequestBuilder.aggregations("collectionAggregation", collectionFilterAggregation);
    });

    final String errorMessage = "Was not able to count collection entities!";
    final SearchResponse<CollectionEntity> searchResponse = osClient.search(
      searchRequestBuilder,
      CollectionEntity.class,
      errorMessage
    );
    return searchResponse.aggregations()
      .entrySet()
      .stream()
      .map(nameToAggregation -> new AbstractMap.SimpleEntry<>(
        nameToAggregation.getKey(),
        extractEntityIndexCounts(nameToAggregation.getValue().sterms().buckets().array())
      ))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public List<CollectionEntity> getAllEntitiesForCollection(final String collectionId) {
    log.debug("Fetching all available entities for collection [{}]", collectionId);
    SearchRequest.Builder requestBuilder = createReportAndDashboardSearchRequest()
      .size(LIST_FETCH_LIMIT)
      .query(QueryDSL.term(COLLECTION_ID, collectionId))
      .scroll(RequestDSL.time(String.valueOf(configurationService.getOpenSearchConfiguration().getScrollTimeoutInSeconds())));

    OpenSearchDocumentOperations.AggregatedResult<Hit<CollectionEntity>> scrollResp;
    try {
      scrollResp = osClient.scrollOs(requestBuilder, CollectionEntity.class);
    } catch (IOException e) {
      log.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }

  @Override
  public Optional<EntityNameResponseDto> getEntityNames(final EntityNameRequestDto requestDto, final String locale) {
    log.debug(String.format("Performing get entity names search request %s", requestDto.toString()));
    MgetResponse<CollectionEntity> multiGetItemResponse = runGetEntityNamesRequest(requestDto);

    EntityNameResponseDto result = new EntityNameResponseDto();
    for (MultiGetResponseItem<CollectionEntity> itemResponse : multiGetItemResponse.docs()) {
      if (itemResponse.isResult()) {
        Optional<CollectionEntity> optionalResponse = OpensearchReaderUtil.processGetResponse(itemResponse.result());

        optionalResponse.ifPresent(entity -> {
          String entityId = entity.getId();
          if (entityId.equals(requestDto.getCollectionId())) {
            result.setCollectionName(entity.getName());
          }
          if (entityId.equals(requestDto.getDashboardId())) {
            result.setDashboardName(getLocalizedDashboardName((DashboardDefinitionRestDto) entity, locale));
          } else if (entityId.equals(requestDto.getReportId())) {
            result.setReportName(getLocalizedReportName(localizationService, entity, locale));
          } else if (entityId.equals(requestDto.getEventBasedProcessId())) {
            result.setEventBasedProcessName(entity.getName());
          }
        });
      }
    }
    return Optional.of(result);
  }

  private Map<EntityType, Long> extractEntityIndexCounts(List<StringTermsBucket> aggregationBuckets) {
    Map<String, Long> bucketNameToDocCount = aggregationBuckets
      .stream()
      .map(bucket -> new AbstractMap.SimpleEntry<>(
        bucket.key(),
        bucket.docCount()
      ))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    final long singleProcessReportCount = getDocCountForIndex(bucketNameToDocCount, new SingleProcessReportIndexOS());
    final long combinedProcessReportCount = getDocCountForIndex(bucketNameToDocCount, new CombinedReportIndexOS());
    final long singleDecisionReportCount = getDocCountForIndex(bucketNameToDocCount, new SingleDecisionReportIndexOS());
    final long dashboardCount = getDocCountForIndex(bucketNameToDocCount, new DashboardIndexOS());
    return ImmutableMap.of(
      EntityType.DASHBOARD,
      dashboardCount,
      EntityType.REPORT,
      singleProcessReportCount + singleDecisionReportCount + combinedProcessReportCount
    );
  }

  private long getDocCountForIndex(final Map<String, Long> keysDocToCount, final IndexMappingCreator indexMapper) {
    if (indexMapper.isCreateFromTemplate()) {
      throw new OptimizeRuntimeException("Cannot fetch the document count for indices created from template");
    }
    return keysDocToCount.getOrDefault(
      optimizeIndexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(indexMapper), 0L);
  }

  private MgetResponse<CollectionEntity> runGetEntityNamesRequest(EntityNameRequestDto requestDto) {
    Map<String, String> indexesToEntitiesId = new HashMap<>();
    indexesToEntitiesId.put(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      requestDto.getReportId()
    );
    indexesToEntitiesId.put(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      requestDto.getReportId()
    );
    indexesToEntitiesId.put(
      COMBINED_REPORT_INDEX_NAME,
      requestDto.getReportId()
    );
    indexesToEntitiesId.put(
      DASHBOARD_INDEX_NAME,
      requestDto.getDashboardId()
    );
    indexesToEntitiesId.put(
      COLLECTION_INDEX_NAME,
      requestDto.getCollectionId()
    );
    indexesToEntitiesId.put(
      EVENT_PROCESS_MAPPING_INDEX_NAME,
      requestDto.getEventBasedProcessId()
    );
    String errorMessage = String.format("Could not get entity names search request %s", requestDto);
    return osClient.mget(CollectionEntity.class, errorMessage, indexesToEntitiesId);
  }

  private SearchRequest.Builder createReportAndDashboardSearchRequest() {
    return new SearchRequest.Builder()
      .index(
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME,
        COMBINED_REPORT_INDEX_NAME,
        DASHBOARD_INDEX_NAME
      );
  }

  private String getLocalizedDashboardName(final DashboardDefinitionRestDto dashboardEntity, final String locale) {
    if (dashboardEntity.isInstantPreviewDashboard()) {
      return localizationService.getLocalizationForInstantPreviewDashboardCode(locale, dashboardEntity.getName());
    } else if (dashboardEntity.isManagementDashboard()) {
      return localizationService.getLocalizationForManagementDashboardCode(locale, dashboardEntity.getName());
    }
    return dashboardEntity.getName();
  }

}
