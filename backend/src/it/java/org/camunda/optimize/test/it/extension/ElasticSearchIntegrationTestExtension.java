/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
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
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil.mapHits;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableIdField;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FREQUENCY_AGGREGATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

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

  private ElasticSearchIntegrationTestExtension(final String customIndexPrefix,
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

  public ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }

  public void refreshAllOptimizeIndices() {
    try {
      RefreshRequest refreshAllIndicesRequest = new RefreshRequest(getIndexNameService().getIndexPrefix() + "*");
      getOptimizeElasticClient().getHighLevelClient()
        .indices()
        .refresh(refreshAllIndicesRequest, getOptimizeElasticClient().requestOptions());
    } catch (Exception e) {
      throw new OptimizeIntegrationTestException("Could not refresh Optimize indices!", e);
    }
  }

  /**
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
      getOptimizeElasticClient().index(request);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Unable to add an entry to elasticsearch", e);
    }
  }

  @SneakyThrows
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

  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return getAllDocumentsOfIndexAs(indexName, type, matchAllQuery());
  }

  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type, final QueryBuilder query) {
    try {
      return getAllDocumentsOfIndicesAs(new String[]{indexName}, type, query);
    } catch (ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate document count for index " + indexName,
        e
      );
    }
  }

  public OptimizeIndexNameService getIndexNameService() {
    return getOptimizeElasticClient().getIndexNameService();
  }

  public SearchResponse getSearchResponseForAllDocumentsOfIndex(final String indexName) {
    return getSearchResponseForAllDocumentsOfIndices(new String[]{indexName});
  }

  @SneakyThrows
  public SearchResponse getSearchResponseForAllDocumentsOfIndices(final String[] indexNames) {
    return getSearchResponseForAllDocumentsOfIndices(indexNames, matchAllQuery());
  }

  @SneakyThrows
  public SearchResponse getSearchResponseForAllDocumentsOfIndices(final String[] indexNames, final QueryBuilder query) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .trackTotalHits(true)
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(indexNames)
      .source(searchSourceBuilder);

    return prefixAwareRestHighLevelClient.search(searchRequest);
  }

  public Integer getDocumentCountOf(final String indexName) {
    return getDocumentCountOf(indexName, QueryBuilders.matchAllQuery());
  }

  public Integer getDocumentCountOf(final String indexName, final QueryBuilder documentQuery) {
    try {
      final CountResponse countResponse = getOptimizeElasticClient()
        .count(new CountRequest(indexName).query(documentQuery));
      return Long.valueOf(countResponse.getCount()).intValue();
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate document count for index " + indexName,
        e
      );
    }
  }

  public Integer getActivityCount() {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .fetchSource(false)
      .size(0)
      .aggregation(
        nested(FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCES)
          .subAggregation(
            count(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION)
              .field(FLOW_NODE_INSTANCES + "." + ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = getOptimizeElasticClient().search(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException("Could not evaluate activity count in process instance indices.", e);
    }

    Nested nested = searchResponse.getAggregations()
      .get(FLOW_NODE_INSTANCES);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION);
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
      searchResponse = getOptimizeElasticClient().search(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate variable instance count in process instance indices.",
        e
      );
    }

    Nested nestedAgg = searchResponse.getAggregations().get(VARIABLES);
    ValueCount countAggregator = nestedAgg.getAggregations().get("count");
    long totalVariableCount = countAggregator.getValue();

    return Long.valueOf(totalVariableCount).intValue();
  }

  public MinMaxStatDto getEndDateRangeForInstancesWithVariables() {
    final AggregationBuilder endDateStatsAgg = AggregationBuilders
      .stats("endDateStats")
      .field(ProcessInstanceIndex.END_DATE)
      .format(OPTIMIZE_DATE_FORMAT);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE))
               .must(nestedQuery(VARIABLES, existsQuery(VARIABLES), ScoreMode.None)))
      .fetchSource(false)
      .aggregation(endDateStatsAgg)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = getOptimizeElasticClient().search(searchRequest);
    } catch (IOException e) {
      log.error("Could not retrieve endDate stats", e);
      throw new OptimizeRuntimeException("Could not retrieve endDate stats", e);
    }
    final Stats minMaxStats = response.getAggregations().get("endDateStats");
    return new MinMaxStatDto(
      minMaxStats.getMin(), minMaxStats.getMax(), minMaxStats.getMinAsString(), minMaxStats.getMaxAsString()
    );
  }

  @SneakyThrows
  public Set<String> getInstanceDefinitionKeysForFinishedInstancesWithVariables() {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE))
               .must(nestedQuery(VARIABLES, existsQuery(VARIABLES), ScoreMode.None)))
      .trackTotalHits(true)
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);
    SearchResponse response = prefixAwareRestHighLevelClient.search(searchRequest);
    return mapHits(response.getHits(), ProcessInstanceDto.class, getObjectMapper()).stream()
      .map(ProcessInstanceDto::getProcessDefinitionKey)
      .collect(Collectors.toSet());
  }

  public void deleteAllOptimizeData() {
    DeleteByQueryRequest request = new DeleteByQueryRequest(getIndexNameService().getIndexPrefix() + "*")
      .setQuery(matchAllQuery())
      .setRefresh(true);

    try {
      getOptimizeElasticClient().getHighLevelClient()
        .deleteByQuery(request, getOptimizeElasticClient().requestOptions());
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not delete all Optimize data", e);
    }
  }

  public void deleteAllDecisionInstanceIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(new DecisionInstanceIndex("*"))
    );
  }

  public void deleteAllProcessInstanceIndices() {
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
      getOptimizeElasticClient().getHighLevelClient()
        .deleteByQuery(request, getOptimizeElasticClient().requestOptions());
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Could not delete data in index " + getIndexNameService().getOptimizeIndexAliasForIndex(index),
        e
      );
    }
  }

  public void deleteIndexOfMapping(final IndexMappingCreator indexMapping) {
    getOptimizeElasticClient().deleteIndex(indexMapping);
  }

  public void deleteAllVariableUpdateInstanceIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "*")
    );
  }

  public boolean indexExists(final String indexOrAliasName) {
    final GetIndexRequest request = new GetIndexRequest(indexOrAliasName);
    try {
      return getOptimizeElasticClient().exists(request);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index already exist.", String.join(",", indexOrAliasName)
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return prefixAwareRestHighLevelClient;
  }

  public void cleanAndVerify() {
    cleanUpElasticSearch();
  }

  @SuppressWarnings(UNUSED)
  public void disableCleanup() {
    haveToClean = false;
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

  public Optional<ProcessInstanceDto> getProcessInstanceById(final String instanceId) {
    return getAllDocumentsOfIndexAs(
      PROCESS_INSTANCE_MULTI_ALIAS,
      ProcessInstanceDto.class,
      termQuery(PROCESS_INSTANCE_ID, instanceId)
    ).stream().findFirst();
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
    return addEventProcessDefinitionDtoToElasticsearch(
      key,
      "eventProcess-" + key,
      null,
      Collections.singletonList(identityDto)
    );
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToElasticsearch(final String key,
                                                                               final String name,
                                                                               final String version,
                                                                               final List<IdentityDto> identityDtos) {
    final List<EventProcessRoleRequestDto<IdentityDto>> roles = identityDtos.stream()
      .filter(Objects::nonNull)
      .map(identityDto -> new IdentityDto(identityDto.getId(), identityDto.getType()))
      .map(EventProcessRoleRequestDto::new)
      .collect(Collectors.toList());
    final EsEventProcessMappingDto eventProcessMappingDto = EsEventProcessMappingDto.builder()
      .id(key)
      .roles(roles)
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
      .onboarded(true)
      .flowNodeData(new ArrayList<>())
      .userTaskNames(Collections.emptyMap())
      .build();
    addEntryToElasticsearch(
      EVENT_PROCESS_DEFINITION_INDEX_NAME, eventProcessDefinitionDto.getId(), eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }

  @SneakyThrows
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(final String esType, final String engine) {
    GetRequest getRequest = new GetRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME).id(EsHelper.constructKey(esType, engine));
    GetResponse response = prefixAwareRestHighLevelClient.get(getRequest);
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

  @SneakyThrows
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    getOptimizeElasticClient().getHighLevelClient()
      .indices()
      .delete(new DeleteIndexRequest(zeebeRecordPrefix + "*"), getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  public void updateZeebeRecordsForPrefix(final String zeebeRecordPrefix, final String indexName,
                                          final BoolQueryBuilder updateQuery, final String updateScript) {
    final UpdateByQueryRequest update = new UpdateByQueryRequest(zeebeRecordPrefix + "_" + indexName + "*")
      .setQuery(updateQuery)
      .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, updateScript, Collections.emptyMap()))
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setRefresh(true);;
    getOptimizeElasticClient().getHighLevelClient().updateByQuery(update, getOptimizeElasticClient().requestOptions());
  }

  @SneakyThrows
  public void updateUserTaskDurations(final String processInstanceId, final String processDefinitionKey,
                                      final long duration) {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("totalDurationField", FLOW_NODE_TOTAL_DURATION)
        .put("idleDurationField", USER_TASK_IDLE_DURATION)
        .put("workDurationField", USER_TASK_WORK_DURATION)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .put("newDuration", String.valueOf(duration))
        .build()
    );
    // @formatter:off
    final String updateScript = substitutor.replace(
      "for (def flowNode : ctx._source.${flowNodesField}) {" +
          "if (flowNode.${flowNodeTypeField}.equals(\"${userTaskFlowNodeType}\")) {" +
            "flowNode.${totalDurationField} = ${newDuration};" +
            "flowNode.${workDurationField} = ${newDuration};" +
            "flowNode.${idleDurationField} = ${newDuration};" +
          "}" +
        "}"
    );
    // @formatter:on

    final UpdateRequest update = new UpdateRequest()
      .index(getProcessInstanceIndexAliasName(processDefinitionKey))
      .id(processInstanceId)
      .script(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, updateScript, Collections.emptyMap()))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    getOptimizeElasticClient().update(update);
  }

  private void deleteCamundaEventIndicesAndEventCountsAndTraces() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"),
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*"),
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_TRACE_STATE_INDEX_PREFIX + "*")
    );
  }

  private void deleteAllProcessInstanceArchiveIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX + "*")
    );
  }

  private void deleteAllEventProcessInstanceIndices() {
    getOptimizeElasticClient().deleteIndexByRawIndexNames(
      getIndexNameService().getOptimizeIndexAliasForIndex(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*")
    );
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

  private <T> List<T> getAllDocumentsOfIndicesAs(final String[] indexNames, final Class<T> type) {
    return getAllDocumentsOfIndicesAs(indexNames, type, matchAllQuery());
  }

  private <T> List<T> getAllDocumentsOfIndicesAs(final String[] indexNames, final Class<T> type,
                                                 final QueryBuilder query) {
    final SearchResponse response = getSearchResponseForAllDocumentsOfIndices(indexNames, query);
    return mapHits(response.getHits(), type, getObjectMapper());
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
      // we allow auto index creation because the Zeebe exporter creates indices for records
      .put("action.auto_create_index", true)
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
      deleteAllProcessInstanceArchiveIndices();
    } catch (Exception e) {
      //nothing to do
      log.error("can't clean optimize indexes", e);
    }
  }

  @SneakyThrows
  private String writeJsonString(final Map.Entry<String, Object> idAndObject) {
    return OBJECT_MAPPER.writeValueAsString(idAndObject.getValue());
  }

  @SneakyThrows
  private void executeBulk(final BulkRequest bulkRequest) {
    getOptimizeElasticClient().bulk(bulkRequest);
  }

}
