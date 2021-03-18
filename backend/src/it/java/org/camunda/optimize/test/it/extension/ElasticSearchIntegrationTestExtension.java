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
import com.google.common.collect.Iterables;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil.mapHits;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableIdField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

/**
 * ElasticSearch Extension including configuration of retrievable ElasticSearch MockServer
 */
@Slf4j
public class ElasticSearchIntegrationTestExtension implements BeforeEachCallback, AfterEachCallback {

  private static final ToXContent.Params XCONTENT_PARAMS_FLAT_SETTINGS = new ToXContent.MapParams(
    Collections.singletonMap("flat_settings", "true")
  );
  private static final String MOCKSERVER_CLIENT_KEY = "MockServer";
  private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
  private static final Map<String, OptimizeElasticsearchClient> CLIENT_CACHE = new HashMap<>();
  private static final ClientAndServer mockServerClient = initMockServer();

  private final String customIndexPrefix;

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;
  private boolean haveToClean;

  public ElasticSearchIntegrationTestExtension() {
    this(true);
  }

  public ElasticSearchIntegrationTestExtension(final boolean haveToClean) {
    this(null, haveToClean);
  }

  public ElasticSearchIntegrationTestExtension(final String customIndexPrefix) {
    this(customIndexPrefix, true);
  }

  public ElasticSearchIntegrationTestExtension(final String customIndexPrefix,
                                               final boolean haveToClean) {
    this.customIndexPrefix = customIndexPrefix;
    this.haveToClean = haveToClean;
    initEsClient();
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    before();
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    // If the MockServer has been used, we reset all expectations and logs and revert to the default client
    if (prefixAwareRestHighLevelClient == CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY)) {
      log.info("Resetting all MockServer expectations and logs");
      mockServerClient.reset();
      log.info("No longer using ES MockServer");
      initEsClient();
    }
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
      createClientAndAddToCache(customIndexPrefix, createConfigurationService());
    }
  }

  private static ClientAndServer initMockServer() {
    log.debug("Setting up ES MockServer on port {}", IntegrationTestConfigurationUtil.getElasticsearchMockServerPort());
    final ElasticsearchConnectionNodeConfiguration esConfig =
      IntegrationTestConfigurationUtil.createItConfigurationService().getFirstElasticsearchConnectionNode();
    return MockServerUtil.createProxyMockServer(
      esConfig.getHost(),
      esConfig.getHttpPort(),
      IntegrationTestConfigurationUtil.getElasticsearchMockServerPort()
    );
  }

  public ClientAndServer useEsMockServer() {
    log.debug("Using ElasticSearch MockServer");
    if (CLIENT_CACHE.containsKey(MOCKSERVER_CLIENT_KEY)) {
      prefixAwareRestHighLevelClient = CLIENT_CACHE.get(MOCKSERVER_CLIENT_KEY);
    } else {
      final ConfigurationService configurationService = createConfigurationService();
      final ElasticsearchConnectionNodeConfiguration esConfig =
        configurationService.getFirstElasticsearchConnectionNode();
      esConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
      esConfig.setHttpPort(mockServerClient.getLocalPort());
      createClientAndAddToCache(MOCKSERVER_CLIENT_KEY, configurationService);
    }
    return mockServerClient;
  }

  private void createClientAndAddToCache(String clientKey, ConfigurationService configurationService) {
    final ElasticsearchConnectionNodeConfiguration esConfig =
      configurationService.getFirstElasticsearchConnectionNode();
    log.info("Creating ES Client with host {} and port {}", esConfig.getHost(), esConfig.getHttpPort());
    prefixAwareRestHighLevelClient = new OptimizeElasticsearchClient(
      ElasticsearchHighLevelRestClientBuilder.build(configurationService),
      new OptimizeIndexNameService(configurationService)
    );
    adjustClusterSettings();
    CLIENT_CACHE.put(clientKey, prefixAwareRestHighLevelClient);
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
  }

  public void addEntriesToElasticsearch(String indexName, Map<String, Object> idToEntryMap) {
    StreamSupport.stream(Iterables.partition(idToEntryMap.entrySet(), 10_000).spliterator(), false)
      .forEach(batch -> {
        final BulkRequest bulkRequest = new BulkRequest();
        for (Map.Entry<String, Object> idAndObject : batch) {
          String json = writeJsonString(idAndObject);
          IndexRequest request = new IndexRequest(indexName)
            .id(idAndObject.getKey())
            .source(json, XContentType.JSON);
          bulkRequest.add(request);
        }
        executeBulk(bulkRequest);
      });
  }

  @SneakyThrows
  private void executeBulk(final BulkRequest bulkRequest) {
    getOptimizeElasticClient().bulk(bulkRequest, RequestOptions.DEFAULT);
  }

  @SneakyThrows
  private String writeJsonString(final Map.Entry<String, Object> idAndObject) {
    return OBJECT_MAPPER.writeValueAsString(idAndObject.getValue());
  }

  public OffsetDateTime getLastProcessedEventTimestampForEventIndexSuffix(final String eventIndexSuffix) throws
                                                                                                         IOException {
    return getLastImportTimestampOfTimestampBasedImportIndex(
      // lowercase as the index names are automatically lowercased and thus the entry contains has a lowercase suffix
      ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + eventIndexSuffix.toLowerCase(),
      ElasticsearchConstants.EVENT_PROCESSING_ENGINE_REFERENCE
    );
  }

  public OffsetDateTime getLastProcessInstanceImportTimestamp() throws IOException {
    return getLastImportTimestampOfTimestampBasedImportIndex(
      PROCESS_INSTANCE_MULTI_ALIAS,
      EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS
    );
  }

  private OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(final String esType, final String engine)
    throws IOException {
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

  public void blockAllProcInstIndices(boolean block) throws IOException {
    List<String> procInstanceIndexKeys = retrieveAllDynamicIndexKeysForPrefix(PROCESS_INSTANCE_INDEX_PREFIX);
    for (String key : procInstanceIndexKeys) {
      blockProcInstIndex(key, block);
    }
  }

  public void blockProcInstIndex(final String definitionKey, boolean block) throws IOException {
    String settingKey = "index.blocks.read_only";
    Settings settings =
      Settings.builder()
        .put(settingKey, block)
        .build();

    UpdateSettingsRequest request = new UpdateSettingsRequest(
      getIndexNameService().getOptimizeIndexAliasForIndex(getProcessInstanceIndexAliasName(definitionKey))
    );
    request.settings(settings);

    getOptimizeElasticClient().getHighLevelClient().indices().putSettings(request, RequestOptions.DEFAULT);
  }

  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    try {
      return getAllDocumentsOfIndicesAs(new String[]{indexName}, type);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private <T> List<T> getAllDocumentsOfIndicesAs(final String[] indexNames, final Class<T> type) {
    final SearchResponse response = getSearchResponseForAllDocumentsOfIndices(indexNames);
    return mapHits(response.getHits(), type, getObjectMapper());
  }

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
    try {
      final CountResponse countResponse = getOptimizeElasticClient()
        .count(new CountRequest(indexName).query(documentQuery), RequestOptions.DEFAULT);
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the import count!", e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        return 0;
      }
      throw e;
    }
  }

  public Integer getActivityCount() {
    return getActivityCount(QueryBuilders.matchAllQuery());
  }

  private Integer getActivityCount(final QueryBuilder processInstanceQuery) {
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
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the activity count!", e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        return 0;
      }
      throw e;
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
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
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
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        return 0;
      }
      throw e;
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
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
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
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        return 0;
      }
      throw e;
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

  public void deleteAllDecisionInstanceIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(new DecisionInstanceIndex("*"))
    );
  }

  public void deleteAllProcessInstanceIndices(){
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(new ProcessInstanceIndex("*"))
    );
  }

  public void deleteAllDocsInIndex(final IndexMappingCreator index) {
    final DeleteByQueryRequest request =
      new DeleteByQueryRequest(getIndexNameService().getOptimizeIndexAliasForIndex(index))
        .setQuery(matchAllQuery())
        .setRefresh(true);

    try {
      getOptimizeElasticClient().getHighLevelClient().deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(
        "Could not delete all data in Index with alias " + getIndexNameService().getOptimizeIndexAliasForIndex(index),
        e
      );
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        // tried to delete instances that do not exist, nothing to do
        return;
      }
      throw e;
    }
  }

  public void deleteIndexOfMapping(final IndexMappingCreator indexMapping) {
    getOptimizeElasticClient().deleteIndex(indexMapping);
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
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));

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
      // all of our tests are running against a one node cluster. Since we're creating a lot of indexes,
      // we are easily hitting the default value of 1000. Thus, we need to increase this value for the test setup.
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

  private void cleanUpElasticSearch() {
    try {
      refreshAllOptimizeIndices();
      deleteAllOptimizeData();
      deleteAllEventProcessInstanceIndices();
      deleteCamundaEventIndicesAndEventCountsAndTraces();
    } catch (Exception e) {
      //nothing to do
      log.error("can't clean optimize indexes", e);
    }
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return getAllDocumentsOfIndexAs(DECISION_DEFINITION_INDEX_NAME, DecisionDefinitionOptimizeDto.class);
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getAllDocumentsOfIndicesAs(
      new String[]{PROCESS_DEFINITION_INDEX_NAME, EVENT_PROCESS_DEFINITION_INDEX_NAME},
      ProcessDefinitionOptimizeDto.class
    );
  }

  public List<TenantDto> getAllTenants() {
    return getAllDocumentsOfIndexAs(TENANT_INDEX_NAME, TenantDto.class);
  }

  public List<EventDto> getAllStoredExternalEvents() {
    return getAllDocumentsOfIndexAs(EXTERNAL_EVENTS_INDEX_NAME, EventDto.class);
  }

  public List<DecisionInstanceDto> getAllDecisionInstances() {
    return getAllDocumentsOfIndexAs(DECISION_INSTANCE_MULTI_ALIAS, DecisionInstanceDto.class);
  }

  public List<ProcessInstanceDto> getAllProcessInstances() {
    return getAllDocumentsOfIndexAs(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class);
  }

  @SneakyThrows
  public List<CamundaActivityEventDto> getAllStoredCamundaActivityEventsForDefinition(final String processDefinitionKey) {
    return getAllDocumentsOfIndexAs(
      new CamundaActivityEventIndex(processDefinitionKey).getIndexName(), CamundaActivityEventDto.class
    );
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key) {
    return addEventProcessDefinitionDtoToElasticsearch(key, "eventProcess-" + key);
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key,
                                                                               final String name) {
    return addEventProcessDefinitionDtoToElasticsearch(
      key,
      name,
      null,
      Collections.singletonList(new IdentityDto(DEFAULT_USERNAME, IdentityType.USER))
    );
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key,
                                                                               final IdentityDto identityDto) {
    return addEventProcessDefinitionDtoToElasticsearch(key, "eventProcess-" + key, identityDto);
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key,
                                                                               final String name,
                                                                               final IdentityDto identityDto) {
    return addEventProcessDefinitionDtoToElasticsearch(key, name, null, Collections.singletonList(identityDto));
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key,
                                                                               final String name,
                                                                               final String version,
                                                                               final List<IdentityDto> identityDtos) {
    final EsEventProcessMappingDto eventProcessMappingDto = EsEventProcessMappingDto.builder()
      .id(key)
      .roles(normalizeToSimpleIdentityDtos(identityDtos))
      .build();
    addEntryToElasticsearch(EVENT_PROCESS_MAPPING_INDEX_NAME, eventProcessMappingDto.getId(), eventProcessMappingDto);

    final String versionValue = Optional.ofNullable(version).orElse("1");
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "-" + version)
      .key(key)
      .name(name)
      .version(versionValue)
      .bpmn20Xml(key + versionValue)
      .deleted(false)
      .flowNodeNames(Collections.emptyMap())
      .userTaskNames(Collections.emptyMap())
      .build();
    addEntryToElasticsearch(
      EVENT_PROCESS_DEFINITION_INDEX_NAME, eventProcessDefinitionDto.getId(), eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }

  public ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                          final String name,
                                                                          final String version) {
    final ProcessDefinitionOptimizeDto processDefinitionDto = ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version)
      .key(key)
      .name(name)
      .version(version)
      .bpmn20Xml(key + version)
      .build();
    addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME, processDefinitionDto.getId(), processDefinitionDto
    );
    return processDefinitionDto;
  }

  public void updateEventProcessRoles(final String eventProcessId, final List<IdentityDto> identityDtos) {
    try {
      UpdateRequest request = new UpdateRequest(EVENT_PROCESS_MAPPING_INDEX_NAME, eventProcessId)
        .script(new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          "ctx._source.roles = params.updatedRoles;",
          Collections.singletonMap(
            "updatedRoles",
            OBJECT_MAPPER.convertValue(normalizeToSimpleIdentityDtos(identityDtos), Object.class)
          )
        ))
        .setRefreshPolicy(IMMEDIATE);
      final UpdateResponse updateResponse = getOptimizeElasticClient().update(request, RequestOptions.DEFAULT);
      if (updateResponse.getShardInfo().getFailed() > 0) {
        final String errorMessage = String.format(
          "Was not able to update event process roles with id [%s].", eventProcessId
        );
        log.error(errorMessage);
        throw new OptimizeIntegrationTestException(errorMessage);
      }
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to update event process roles.", e);
    }
  }

  @SneakyThrows
  public List<VariableUpdateInstanceDto> getAllStoredVariableUpdateInstanceDtos() {
    return getAllDocumentsOfIndexAs(
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "_*", VariableUpdateInstanceDto.class
    );
  }

  public void deleteAllExternalEventIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(EXTERNAL_EVENTS_INDEX_NAME + "_*")
    );
  }

  public void deleteCamundaEventIndicesAndEventCountsAndTraces() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"),
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*"),
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_TRACE_STATE_INDEX_PREFIX + "*")
    );
  }

  private void deleteAllEventProcessInstanceIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*")
    );
  }

  public void deleteAllVariableUpdateInstanceIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "*")
    );
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return prefixAwareRestHighLevelClient;
  }

  public String getEsVersion() {
    try {
      return prefixAwareRestHighLevelClient.getHighLevelClient().info(RequestOptions.DEFAULT).getVersion().getNumber();
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not retrieve elasticsearch version.", e);
    }
  }

  private List<EventProcessRoleRequestDto<IdentityDto>> normalizeToSimpleIdentityDtos(final List<IdentityDto> identityDtos) {
    return identityDtos.stream()
      .filter(Objects::nonNull)
      .map(identityDto -> new IdentityDto(identityDto.getId(), identityDto.getType()))
      .map(EventProcessRoleRequestDto::new)
      .collect(Collectors.toList());
  }

  private List<String> retrieveAllDynamicIndexKeysForPrefix(final String dynamicIndexPrefix) {
    final GetAliasesResponse aliases;
    try {
      aliases = getOptimizeElasticClient().getAlias(
        new GetAliasesRequest(dynamicIndexPrefix + "*"), RequestOptions.DEFAULT
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for dynamic index prefix " + dynamicIndexPrefix);
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(fullAliasName ->
             fullAliasName.substring(fullAliasName.lastIndexOf(dynamicIndexPrefix) + dynamicIndexPrefix.length()))
      .collect(toList());
  }
}
