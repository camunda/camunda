/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.CustomDeserializer;
import org.camunda.optimize.service.util.mapper.CustomSerializer;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getVersionedOptimizeIndexNameForTypeMapping;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_ID;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElasticSearchIntegrationTestRule extends TestWatcher {
  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchIntegrationTestRule.class);

  private static ObjectMapper objectMapper;
  private static RestHighLevelClient esClient;
  private static boolean haveToClean = true;
  private static ConfigurationService configurationService;

  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  public ElasticSearchIntegrationTestRule() {
  }

  @Override
  protected void starting(Description description) {
    initConfigurationService();
    initObjectMapper();
    this.initEsClient();
    if (haveToClean) {
      logger.info("Cleaning elasticsearch...");
      this.cleanAndVerify();
      logger.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
    }
  }

  private void initEsClient() {
    if (esClient == null) {
      esClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);
    }
  }

  private void initConfigurationService() {
    if (configurationService == null) {
      configurationService = new ConfigurationService();
    }
  }

  private void initObjectMapper() {
    if (objectMapper == null) {

      DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
      JavaTimeModule javaTimeModule = new JavaTimeModule();
      javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(dateTimeFormatter));
      javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(dateTimeFormatter));

      objectMapper = Jackson2ObjectMapperBuilder
        .json()
        .modules(javaTimeModule)
        .featuresToDisable(
          SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
          DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
          DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
          DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
        )
        .featuresToEnable(
          JsonParser.Feature.ALLOW_COMMENTS,
          SerializationFeature.INDENT_OUTPUT
        )
        .build();
    }
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void refreshAllOptimizeIndices() {
    try {
      RefreshRequest refreshAllIndicesRequest = new RefreshRequest();
      getEsClient().indices().refresh(refreshAllIndicesRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  /**
   * parsed to json and then later
   * This class adds a document entry to elasticsearch (ES). Thereby, the
   * the entry is added to the optimize index and the given type under
   * the given id.
   * <p>
   * The object needs be a POJO, which is then converted to json. Thus, the entry
   * results in every object member variable name is going to be mapped to the
   * field name in ES and every content of that variable is going to be the
   * content of the field.
   *
   * @param type  where the entry is added.
   * @param id    under which the entry is added.
   * @param entry a POJO specifying field names and their contents.
   */
  public void addEntryToElasticsearch(String type, String id, Object entry) {
    try {
      String json = objectMapper.writeValueAsString(entry);
      IndexRequest request = new IndexRequest(getOptimizeIndexAliasForType(type), type, id)
        .source(json, XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE); // necessary because otherwise I can't search for the entry immediately
      getEsClient().index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
    addEntryToTracker(type, id);
  }

  public OffsetDateTime getLastProcessInstanceImportTimestamp() throws IOException {
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(TIMESTAMP_BASED_IMPORT_INDEX_TYPE),
      TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
      EsHelper.constructKey(ElasticsearchConstants.PROC_INSTANCE_TYPE, "1")
    );

    String content = esClient.get(getRequest, RequestOptions.DEFAULT).getSourceAsString();
    TimestampBasedImportIndexDto timestampBasedImportIndexDto = objectMapper.readValue(
      content,
      TimestampBasedImportIndexDto.class
    );
    return timestampBasedImportIndexDto.getTimestampOfLastEntity();
  }

  public void blockProcInstIndex(boolean block) throws IOException {
    String settingKey = "index.blocks.read_only";
    Settings settings =
      Settings.builder()
        .put(settingKey, block)
        .build();

    UpdateSettingsRequest request = new UpdateSettingsRequest(OptimizeIndexNameHelper.getOptimizeIndexAliasForType(
      PROC_INSTANCE_TYPE));
    request.settings(settings);

    esClient.indices().putSettings(request, RequestOptions.DEFAULT);
  }

  @SneakyThrows
  public SearchResponse getSearchResponseForAllDocumentsOfType(final String elasticsearchType) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(elasticsearchType))
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return esClient.search(searchRequest, RequestOptions.DEFAULT);
  }

  public Integer getDocumentCountOf(final String elasticsearchType) {
    return getDocumentCountOf(elasticsearchType, QueryBuilders.matchAllQuery());
  }

  public Integer getDocumentCountOf(final String elasticsearchType, final QueryBuilder documentQuery) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(documentQuery)
      .fetchSource(false)
      .size(0);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(elasticsearchType))
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getEsClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the import count!", e);
    }
    return Long.valueOf(searchResponse.getHits().getTotalHits()).intValue();
  }

  public Integer getActivityCount() {
    return getActivityCount(QueryBuilders.matchAllQuery());
  }

  public Integer getActivityCount(final QueryBuilder processInstanceQuery) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .size(0)
      .aggregation(
        nested(EVENTS, EVENTS)
          .subAggregation(
            count(EVENTS + "_count")
              .field(EVENTS + "." + ProcessInstanceType.EVENT_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getEsClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the activity count!", e);
    }

    Nested nested = searchResponse.getAggregations()
      .get(EVENTS);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(EVENTS + "_count");
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  public Integer getVariableInstanceCount() {
    return getVariableInstanceCount(QueryBuilders.matchAllQuery());
  }

  public Integer getVariableInstanceCount(final QueryBuilder processInstanceQuery) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(processInstanceQuery)
      .fetchSource(false)
      .size(0);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    for (String variableTypeFieldLabel : ProcessVariableHelper.allVariableTypeFieldLabels) {
      searchSourceBuilder.aggregation(
        nested(variableTypeFieldLabel, variableTypeFieldLabel)
          .subAggregation(
            count(variableTypeFieldLabel + "_count")
              .field(variableTypeFieldLabel + "." + VARIABLE_ID)
          )
      );
    }

    SearchResponse searchResponse;
    try {
      searchResponse = getEsClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the variable instance count!", e);
    }

    long totalVariableCount = 0L;
    for (String variableTypeFieldLabel : ProcessVariableHelper.allVariableTypeFieldLabels) {
      Nested nestedAgg = searchResponse.getAggregations().get(variableTypeFieldLabel);
      ValueCount countAggregator = nestedAgg.getAggregations()
        .get(variableTypeFieldLabel + "_count");
      totalVariableCount += countAggregator.getValue();
    }

    return Long.valueOf(totalVariableCount).intValue();
  }

  public void deleteAllOptimizeData() {
    DeleteByQueryRequest request = new DeleteByQueryRequest("_all")
      .setQuery(matchAllQuery())
      .setRefresh(true);

    try {
      getEsClient().deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  public void deleteIndexOfType(final TypeMappingCreator type) {
    try {
      getEsClient().indices().delete(
        new DeleteIndexRequest(getVersionedOptimizeIndexNameForTypeMapping(type)),
        RequestOptions.DEFAULT
      );
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  public void cleanAndVerify() {
    cleanUpElasticSearch();
    assureElasticsearchIsClean();
  }

  private void addEntryToTracker(String type, String id) {
    if (!documentEntriesTracker.containsKey(type)) {
      List<String> idList = new LinkedList<>();
      idList.add(id);
      documentEntriesTracker.put(type, idList);
    } else {
      List<String> ids = documentEntriesTracker.get(type);
      ids.add(id);
      documentEntriesTracker.put(type, ids);
    }
  }

  private void cleanUpElasticSearch() {
    try {
      refreshAllOptimizeIndices();
      deleteAllOptimizeData();
    } catch (Exception e) {
      //nothing to do
      logger.error("can't clean optimize indexes", e);
    }
  }

  private void assureElasticsearchIsClean() {
    try {
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(QueryBuilders.matchAllQuery());
      SearchRequest searchRequest = new SearchRequest();
      searchRequest.indices(OptimizeIndexNameHelper.OPTIMIZE_INDEX_PREFIX + "*");
      searchRequest.source(searchSourceBuilder);
      SearchResponse searchResponse = getEsClient().search(searchRequest, RequestOptions.DEFAULT);

      Long hits = searchResponse.getHits().getTotalHits();
      assertThat("Elasticsearch should be clean after Test!", hits, is(0L));
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("Could not check if elasticsearch is clean!", e);
    }
  }

  public RestHighLevelClient getEsClient() {
    return esClient;
  }

  public void disableCleanup() {
    haveToClean = false;
  }
}
