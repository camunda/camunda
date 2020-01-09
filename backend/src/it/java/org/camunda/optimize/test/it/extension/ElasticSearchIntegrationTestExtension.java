/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.EsHelper;
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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableIdField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

/**
 * ElasticSearch Extension
 */
@Slf4j
public class ElasticSearchIntegrationTestExtension implements BeforeEachCallback {
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

  public ElasticSearchIntegrationTestExtension() {
    this(null);
  }

  public ElasticSearchIntegrationTestExtension(final String customIndexPrefix) {
    this.customIndexPrefix = customIndexPrefix;
    initEsClient();
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    before();
  }

  private void before() {
    if (haveToClean) {
      log.info("Cleaning elasticsearch...");
      this.cleanAndVerify();
      log.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
    }
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
      adjustClusterSettings();
      CLIENT_CACHE.put(customIndexPrefix, prefixAwareRestHighLevelClient);
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
   * @param indexName where the entry is added.
   * @param id        under which the entry is added.
   * @param entry     a POJO specifying field names and their contents.
   */
  public void addEntryToElasticsearch(String indexName, String id, Object entry) {
    try {
      String json = OBJECT_MAPPER.writeValueAsString(entry);
      IndexRequest request = new IndexRequest(indexName)
        .id(id)
        .source(json, XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE); // necessary because otherwise I can't search for the entry immediately
      getOptimizeElasticClient().index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
    addEntryToTracker(indexName, id);
  }

  public OffsetDateTime getLastProcessedEventTimestamp() throws IOException {
    return getLastImportTimestampOfTimestampBasedImportIndex(
      ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE,
      ElasticsearchConstants.EVENT_PROCESSING_ENGINE_REFERENCE
    );
  }

  public OffsetDateTime getLastProcessInstanceImportTimestamp() throws IOException {
    return getLastImportTimestampOfTimestampBasedImportIndex(ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME, "1");
  }

  private OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(String esType, String engine) throws
                                                                                                         IOException {
    GetRequest getRequest = new GetRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME).id(EsHelper.constructKey(esType, engine));
    GetResponse response = prefixAwareRestHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
    if (response.isExists()) {
      return OBJECT_MAPPER.readValue(response.getSourceAsString(), TimestampBasedImportIndexDto.class)
        .getTimestampOfLastEntity();
    } else {
      throw new NotFoundException(String.format(
        "Timestamp based import index does not exist: esType: {%s}, engine: {%s}",
        esType,
        engine
      ));
    }
  }

  public void blockProcInstIndex(boolean block) throws IOException {
    String settingKey = "index.blocks.read_only";
    Settings settings =
      Settings.builder()
        .put(settingKey, block)
        .build();

    UpdateSettingsRequest request = new UpdateSettingsRequest(
      getIndexNameService().getOptimizeIndexAliasForIndex(PROCESS_INSTANCE_INDEX_NAME)
    );
    request.settings(settings);

    getOptimizeElasticClient().getHighLevelClient().indices().putSettings(request, RequestOptions.DEFAULT);
  }

  @SneakyThrows
  public SearchResponse getSearchResponseForAllDocumentsOfIndex(final String indexName) {
    return getSearchResponseForAllDocumentsOfIndices(new String[]{indexName});
  }

  @SneakyThrows
  public SearchResponse getSearchResponseForAllDocumentsOfIndices(final String[] indexNames) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .trackTotalHits(true)
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(indexNames)
      .source(searchSourceBuilder);

    return prefixAwareRestHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
  }

  public Integer getDocumentCountOf(final String indexName) {
    return getDocumentCountOf(indexName, QueryBuilders.matchAllQuery());
  }

  public Integer getDocumentCountOf(final String indexName, final QueryBuilder documentQuery) {
    final CountRequest countRequest = new CountRequest(indexName)
      .source(new SearchSourceBuilder().query(documentQuery));

    try {
      final CountResponse countResponse = getOptimizeElasticClient().count(countRequest, RequestOptions.DEFAULT);
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the import count!", e);
    }
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
              .field(EVENTS + "." + ProcessInstanceIndex.EVENT_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_INDEX_NAME)
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
      .indices(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    searchSourceBuilder.aggregation(
      nested(VARIABLES, VARIABLES)
        .subAggregation(
          count("count")
            .field(getNestedVariableIdField())
        )
    );

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the variable instance count!", e);
    }

    Nested nestedAgg = searchResponse.getAggregations().get(VARIABLES);
    ValueCount countAggregator = nestedAgg.getAggregations().get("count");
    long totalVariableCount = countAggregator.getValue();

    return Long.valueOf(totalVariableCount).intValue();
  }

  public Integer getVariableInstanceCount(String variableName) {
    final QueryBuilder query = nestedQuery(
      VARIABLES,
      boolQuery().must(termQuery(getNestedVariableNameField(), variableName)),
      ScoreMode.None
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .size(0);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    String VARIABLE_COUNT_AGGREGATION = VARIABLES + "_count";
    String NESTED_VARIABLE_AGGREGATION = "nestedAggregation";
    searchSourceBuilder.aggregation(
      nested(
        NESTED_VARIABLE_AGGREGATION,
        VARIABLES
      )
        .subAggregation(
          count(VARIABLE_COUNT_AGGREGATION)
            .field(getNestedVariableIdField())
        )
    );

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the variable instance count!", e);
    }

    Nested nestedAgg = searchResponse.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    ValueCount countAggregator = nestedAgg.getAggregations().get(VARIABLE_COUNT_AGGREGATION);
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

  public void deleteIndexOfMapping(final IndexMappingCreator indexMapping) {
    try {
      getOptimizeElasticClient().getHighLevelClient().indices().delete(
        new DeleteIndexRequest(getIndexNameService().getVersionedOptimizeIndexNameForIndexMapping(indexMapping)),
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

  private void adjustClusterSettings() {
    Settings settings = Settings.builder()
      // disable automatic index creations to fail early in integration tests
      .put("action.auto_create_index", false)
      // all of our tests are running against a one node cluster. Since we're creating a lot of indexes
      // and each index creates 5 shards per default, we are easily hitting the default value of 1000.
      // Thus, we need to increase this value for the test setup.
      .put("cluster.max_shards_per_node", 10_000)
      .build();
    ClusterUpdateSettingsRequest clusterUpdateSettingsRequest = new ClusterUpdateSettingsRequest();
    clusterUpdateSettingsRequest.persistentSettings(settings);
    try (XContentBuilder builder = jsonBuilder()) {
      // low level request as we need body serialized with flat_settings option for AWS hosted elasticsearch support
      Request request = new Request("PUT", "/_cluster/settings");
      request.setJsonEntity(Strings.toString(
        clusterUpdateSettingsRequest.toXContent(builder, XCONTENT_PARAMS_FLAT_SETTINGS)
      ));
      prefixAwareRestHighLevelClient.getLowLevelClient().performRequest(request);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not update cluster settings!", e);
    }
  }

  private void addEntryToTracker(String indexName, String id) {
    if (!documentEntriesTracker.containsKey(indexName)) {
      List<String> idList = new LinkedList<>();
      idList.add(id);
      documentEntriesTracker.put(indexName, idList);
    } else {
      List<String> ids = documentEntriesTracker.get(indexName);
      ids.add(id);
      documentEntriesTracker.put(indexName, ids);
    }
  }

  private void cleanUpElasticSearch() {
    try {
      refreshAllOptimizeIndices();
      deleteAllOptimizeData();
      deleteAllEventProcessInstanceIndices();
    } catch (Exception e) {
      //nothing to do
      log.error("can't clean optimize indexes", e);
    }
  }

  public void deleteAllEventIndices() {
    DeleteIndexRequest request = new DeleteIndexRequest(
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_INDEX_NAME + "_*")
    );

    try {
      getOptimizeElasticClient().getHighLevelClient().indices().delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all event indices.", e);
    }
  }

  private void deleteAllEventProcessInstanceIndices() {
    DeleteIndexRequest request = new DeleteIndexRequest(
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*")
    );

    try {
      getOptimizeElasticClient().getHighLevelClient().indices().delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all event process indices.", e);
    }
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return prefixAwareRestHighLevelClient;
  }
}
