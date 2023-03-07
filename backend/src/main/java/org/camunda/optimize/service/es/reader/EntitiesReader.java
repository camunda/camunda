/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil.atLeastOneResponseExistsForMultiGet;
import static org.camunda.optimize.service.es.reader.ReportReader.REPORT_DATA_XML_PROPERTY;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.OWNER;
import static org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Slf4j
public class EntitiesReader {

  private static final String AGG_BY_INDEX_COUNT = "byIndexCount";
  private static final String[] ENTITY_LIST_EXCLUDES = {REPORT_DATA_XML_PROPERTY};
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final LocalizationService localizationService;
  private final ObjectMapper objectMapper;


  public List<CollectionEntity> getAllPrivateEntities() {
    return getAllPrivateEntities(null);
  }

  public List<CollectionEntity> getAllPrivateEntities(final String userId) {
    log.debug("Fetching all available entities for user [{}]", userId);

    final BoolQueryBuilder query = boolQuery().mustNot(existsQuery(COLLECTION_ID))
      .minimumShouldMatch(1)
      .should(termQuery(MANAGEMENT_DASHBOARD, false))
      .should(termQuery(DATA + "." + MANAGEMENT_REPORT, false))
      .should(boolQuery().mustNot(existsQuery(MANAGEMENT_DASHBOARD)).mustNot(existsQuery(DATA + "." + MANAGEMENT_DASHBOARD)));

    if (userId != null) {
      query.must(termQuery(OWNER, userId));
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, ENTITY_LIST_EXCLUDES);
    SearchRequest searchRequest = createReportAndDashboardSearchRequest().source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      CollectionEntity.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  public Map<String, Map<EntityType, Long>> countEntitiesForCollections(final List<? extends BaseCollectionDefinitionDto> collections) {
    log.debug(
      "Counting all available entities for collection ids [{}]",
      collections.stream().map(BaseCollectionDefinitionDto::getId).collect(Collectors.toList())
    );

    if (collections.isEmpty()) {
      return new HashMap<>();
    }

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(termsQuery(
      COLLECTION_ID,
      collections.stream()
        .map(BaseCollectionDefinitionDto::getId)
        .collect(Collectors.toList())
    )).size(0);

    collections.forEach(collection -> {
      final String collectionId = collection.getId();
      final FilterAggregationBuilder byCollectionIdFilterAggregation = filter(
        collectionId,
        boolQuery().filter(termQuery(
          COLLECTION_ID,
          collectionId
        ))
      );
      searchSourceBuilder.aggregation(byCollectionIdFilterAggregation);
      final TermsAggregationBuilder byIndexNameAggregation = terms(AGG_BY_INDEX_COUNT).field("_index");
      byCollectionIdFilterAggregation.subAggregation(byIndexNameAggregation);
    });

    final SearchRequest searchRequest = createReportAndDashboardSearchRequest().source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return searchResponse.getAggregations()
        .asList()
        .stream()
        .map(agg -> (Filter) agg)
        .map(collectionFilterAggregation -> new AbstractMap.SimpleEntry<>(
          collectionFilterAggregation.getName(),
          extractEntityIndexCounts(collectionFilterAggregation)
        ))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to count collection entities!", e);
    }
  }

  public List<CollectionEntity> getAllEntitiesForCollection(final String collectionId) {
    log.debug("Fetching all available entities for collection [{}]", collectionId);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(termQuery(COLLECTION_ID, collectionId))
      .size(LIST_FETCH_LIMIT);
    return runEntitiesSearchRequest(searchSourceBuilder);
  }

  public Optional<EntityNameResponseDto> getEntityNames(final EntityNameRequestDto requestDto, final String locale) {
    log.debug(String.format("Performing get entity names search request %s", requestDto.toString()));
    MultiGetResponse multiGetItemResponse = runGetEntityNamesRequest(requestDto);

    if (!atLeastOneResponseExistsForMultiGet(multiGetItemResponse)) {
      return Optional.empty();
    }

    EntityNameResponseDto result = new EntityNameResponseDto();
    for (MultiGetItemResponse itemResponse : multiGetItemResponse) {
      GetResponse response = itemResponse.getResponse();
      if (response.isExists()) {
        String entityId = response.getId();
        CollectionEntity entity = readCollectionEntity(response, entityId);
        if (entityId.equals(requestDto.getCollectionId())) {
          result.setCollectionName(entity.getName());
        }

        if (entityId.equals(requestDto.getDashboardId())) { // no "else if" here in case request comes from a magic link
          result.setDashboardName(getLocalizedDashboardName((DashboardDefinitionRestDto) entity, locale));
        } else if (entityId.equals(requestDto.getReportId())) {
          result.setReportName(getLocalizedReportName(entity, locale));
        } else if (entityId.equals(requestDto.getEventBasedProcessId())) {
          result.setEventBasedProcessName(entity.getName());
        }
      }
    }

    return Optional.ofNullable(result);
  }

  private Map<EntityType, Long> extractEntityIndexCounts(final Filter collectionFilterAggregation) {
    final Terms byIndexNameTerms = collectionFilterAggregation.getAggregations().get(AGG_BY_INDEX_COUNT);
    final long singleProcessReportCount = getDocCountForIndex(byIndexNameTerms, new SingleProcessReportIndex());
    final long combinedProcessReportCount = getDocCountForIndex(byIndexNameTerms, new CombinedReportIndex());
    final long singleDecisionReportCount = getDocCountForIndex(byIndexNameTerms, new SingleDecisionReportIndex());
    final long dashboardCount = getDocCountForIndex(byIndexNameTerms, new DashboardIndex());
    return ImmutableMap.of(
      EntityType.DASHBOARD,
      dashboardCount,
      EntityType.REPORT,
      singleProcessReportCount + singleDecisionReportCount + combinedProcessReportCount
    );
  }

  private long getDocCountForIndex(final Terms byIndexNameTerms, final IndexMappingCreator indexMapper) {
    if (indexMapper.isCreateFromTemplate()) {
      throw new OptimizeRuntimeException("Cannot fetch the document count for indices created from template");
    }
    return Optional.ofNullable(byIndexNameTerms.getBucketByKey(optimizeIndexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(
      indexMapper))).map(MultiBucketsAggregation.Bucket::getDocCount).orElse(0L);
  }

  private CollectionEntity readCollectionEntity(final GetResponse response, final String entityId) {
    CollectionEntity entity;
    try {
      entity = objectMapper.readValue(response.getSourceAsString(), CollectionEntity.class);
    } catch (IOException e) {
      String reason = String.format("Can't read collection entity with id [%s].", entityId);
      throw new OptimizeRuntimeException(reason, e);
    }
    return entity;
  }

  private MultiGetResponse runGetEntityNamesRequest(EntityNameRequestDto requestDto) {
    MultiGetRequest request = new MultiGetRequest();
    addGetEntityToRequest(request, requestDto.getReportId(), SINGLE_PROCESS_REPORT_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getReportId(), SINGLE_DECISION_REPORT_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getReportId(), COMBINED_REPORT_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getDashboardId(), DASHBOARD_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getCollectionId(), COLLECTION_INDEX_NAME);
    addGetEntityToRequest(request, requestDto.getEventBasedProcessId(), EVENT_PROCESS_MAPPING_INDEX_NAME);
    if (request.getItems().isEmpty()) {
      throw new BadRequestException("No ids for entity name request provided");
    }

    MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request);
    } catch (IOException e) {
      String reason = String.format("Could not get entity names search request %s", requestDto.toString());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  private void addGetEntityToRequest(final MultiGetRequest request, final String entityId, final String entityIndexName) {
    if (entityId != null) {
      request.add(new MultiGetRequest.Item(entityIndexName, entityId));
    }
  }

  private List<CollectionEntity> runEntitiesSearchRequest(final SearchSourceBuilder searchSourceBuilder) {
    SearchRequest searchRequest = createReportAndDashboardSearchRequest().source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      CollectionEntity.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  private SearchRequest createReportAndDashboardSearchRequest() {
    return new SearchRequest(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SINGLE_DECISION_REPORT_INDEX_NAME,
      COMBINED_REPORT_INDEX_NAME,
      DASHBOARD_INDEX_NAME
    );
  }


  private String getLocalizedDashboardName(final DashboardDefinitionRestDto dashboardEntity, final String locale) {
    try {
      if (dashboardEntity.isInstantPreviewDashboard()) {
        return localizationService.getLocalizationForInstantPreviewDashboardCode(locale, dashboardEntity.getName());
      } else if (dashboardEntity.isManagementDashboard()) {
        return localizationService.getLocalizationForManagementDashboardCode(locale, dashboardEntity.getName());
      }
    } catch (IOException e) {
      log.error(
        "Failed to localize name for Management or Instant Preview Dashboard with ID [{}] and name [{}]",
        dashboardEntity.getId(),
        dashboardEntity.getName()
      );
    }
    return dashboardEntity.getName();
  }

  private String getLocalizedReportName(final CollectionEntity reportEntity, final String locale) {
    if (reportEntity instanceof SingleProcessReportDefinitionRequestDto) {
      try {
        if (((SingleProcessReportDefinitionRequestDto) reportEntity).getData().isInstantPreviewReport()) {
          return localizationService.getLocalizationForInstantPreviewReportCode(locale, reportEntity.getName());
        } else if (((SingleProcessReportDefinitionRequestDto) reportEntity).getData().isManagementReport()) {
          return localizationService.getLocalizationForManagementReportCode(locale, reportEntity.getName());
        }
      } catch (IOException e) {
        log.error(
          "Failed to localize name for Management or Instant Preview Report with ID [{}] and name [{}]",
          reportEntity.getId(),
          reportEntity.getName()
        );
      }
    }
    return reportEntity.getName();
  }
}
