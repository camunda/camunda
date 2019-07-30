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
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.CustomDeserializer;
import org.camunda.optimize.service.util.mapper.CustomSerializer;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_ID;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Slf4j
public class ElasticSearchIntegrationTestRule extends TestWatcher {
  private static final ToXContent.Params XCONTENT_PARAMS_FLAT_SETTINGS = new ToXContent.MapParams(
    Collections.singletonMap("flat_settings", "true")
  );

  private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
  private static final Map<String, OptimizeElasticsearchClient> CLIENT_CACHE = new HashMap<>();

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;

  private boolean haveToClean = true;

  private final String customIndexPrefix;
  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  public ElasticSearchIntegrationTestRule() {
    this(null);
  }

  public ElasticSearchIntegrationTestRule(final String customIndexPrefix) {
    this.customIndexPrefix = customIndexPrefix;
    initEsClient();
  }

  private void initEsClient() {
    if (CLIENT_CACHE.containsKey(customIndexPrefix)) {
      prefixAwareRestHighLevelClient = CLIENT_CACHE.get(customIndexPrefix);
    } else {
      final ConfigurationService configurationService = createConfigurationService();
      final OptimizeIndexNameService indexNameService = new OptimizeIndexNameService(configurationService);
      prefixAwareRestHighLevelClient = new OptimizeElasticsearchClient(
        ElasticsearchHighLevelRestClientBuilder.build(configurationService),
        indexNameService
      );
      disableAutomaticIndexCreation();
      CLIENT_CACHE.put(customIndexPrefix, prefixAwareRestHighLevelClient);
    }
  }

  @Override
  protected void starting(Description description) {
    if (haveToClean) {
      log.info("Cleaning elasticsearch...");
      this.cleanAndVerify();
      log.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
    }
  }

  public ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }

  public void refreshAllOptimizeIndices() {
    try {
      RefreshRequest refreshAllIndicesRequest = new RefreshRequest(getIndexNameService().getIndexPrefix() + "*");
      getOptimizeElasticClient().getHighLevelClient()
        .indices()
        .refresh(refreshAllIndicesRequest, RequestOptions.DEFAULT);
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
      String json = OBJECT_MAPPER.writeValueAsString(entry);
      IndexRequest request = new IndexRequest(type, type, id)
        .source(json, XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE); // necessary because otherwise I can't search for the entry immediately
      getOptimizeElasticClient().index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
    addEntryToTracker(type, id);
  }

  public OffsetDateTime getLastProcessInstanceImportTimestamp() throws IOException {
    GetRequest getRequest = new GetRequest(
      TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
      TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
      EsHelper.constructKey(ElasticsearchConstants.PROC_INSTANCE_TYPE, "1")
    );

    String content = prefixAwareRestHighLevelClient.get(getRequest, RequestOptions.DEFAULT).getSourceAsString();
    TimestampBasedImportIndexDto timestampBasedImportIndexDto = OBJECT_MAPPER.readValue(
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

    UpdateSettingsRequest request = new UpdateSettingsRequest(
      getIndexNameService().getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE)
    );
    request.settings(settings);

    getOptimizeElasticClient().getHighLevelClient().indices().putSettings(request, RequestOptions.DEFAULT);
  }

  @SneakyThrows
  public SearchResponse getSearchResponseForAllDocumentsOfType(final String elasticsearchType) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(elasticsearchType)
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return prefixAwareRestHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
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
      .indices(elasticsearchType)
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
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
      .indices(PROC_INSTANCE_TYPE)
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
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
      .indices(PROC_INSTANCE_TYPE)
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
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
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

  public Integer getVariableInstanceCount(String variableName, VariableType variableType) {
    final String variableTypeFieldLabel = ProcessVariableHelper.variableTypeToFieldLabel(variableType);

    final QueryBuilder query = nestedQuery(
      variableTypeFieldLabel,
      boolQuery().must(termQuery(getNestedVariableNameFieldLabel(variableTypeFieldLabel), variableName)),
      ScoreMode.None
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .size(0);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROC_INSTANCE_TYPE)
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    searchSourceBuilder.aggregation(
      nested(
        "nestedAggregation",
        variableTypeFieldLabel
      )
        .subAggregation(
          count(variableTypeFieldLabel + "_count")
            .field(variableTypeFieldLabel + "." + VARIABLE_ID)

        )
    );

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the variable instance count!", e);
    }

    Nested nestedAgg = searchResponse.getAggregations().get("nestedAggregation");
    ValueCount countAggregator = nestedAgg.getAggregations().get(variableTypeFieldLabel + "_count");
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  public void deleteAllOptimizeData() {
    DeleteByQueryRequest request = new DeleteByQueryRequest(getIndexNameService().getIndexPrefix() + "*")
      .setQuery(matchAllQuery())
      .setRefresh(true);

    try {
      getOptimizeElasticClient().getHighLevelClient().deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  public void deleteIndexOfType(final TypeMappingCreator type) {
    try {
      getOptimizeElasticClient().getHighLevelClient().indices().delete(
        new DeleteIndexRequest(getIndexNameService().getVersionedOptimizeIndexNameForTypeMapping(type)),
        RequestOptions.DEFAULT
      );
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  private OptimizeIndexNameService getIndexNameService() {
    return getOptimizeElasticClient().getIndexNameService();
  }

  public void cleanAndVerify() {
    cleanUpElasticSearch();
  }

  public void disableCleanup() {
    haveToClean = false;
  }

  private ConfigurationService createConfigurationService() {
    final ConfigurationService configurationService = IntegrationTestConfigurationUtil.createItConfigurationService();
    if (customIndexPrefix != null) {
      configurationService.setEsIndexPrefix(configurationService.getEsIndexPrefix() + customIndexPrefix);
    }
    return configurationService;
  }

  private static ObjectMapper createObjectMapper() {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(dateTimeFormatter));

    return Jackson2ObjectMapperBuilder
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

  private void disableAutomaticIndexCreation() {
    Settings settings = Settings.builder()
      .put("action.auto_create_index", false)
      .build();
    ClusterUpdateSettingsRequest clusterUpdateSettingsRequest = new ClusterUpdateSettingsRequest();
    clusterUpdateSettingsRequest.persistentSettings(settings);
    try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
      // low level request as we need body serialized with flat_settings option for AWS hosted elasticsearch support
      Request request = new Request("PUT", "/_cluster/settings");
      request.setJsonEntity(Strings.toString(
        clusterUpdateSettingsRequest.toXContent(builder, XCONTENT_PARAMS_FLAT_SETTINGS)
      ));
      prefixAwareRestHighLevelClient.getLowLevelClient().performRequest(request);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not update index settings!", e);
    }
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
      log.error("can't clean optimize indexes", e);
    }
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return prefixAwareRestHighLevelClient;
  }
}
