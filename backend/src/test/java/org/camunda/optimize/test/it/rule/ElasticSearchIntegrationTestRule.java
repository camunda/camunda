package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.CustomDeserializer;
import org.camunda.optimize.service.util.CustomSerializer;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.util.PropertyUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_ID;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElasticSearchIntegrationTestRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(ElasticSearchIntegrationTestRule.class);
  private static final String DEFAULT_PROPERTIES_PATH = "integration-rules.properties";
  private Properties properties;
  private static ObjectMapper objectMapper;
  private static Client esclient;
  private boolean haveToClean = true;

  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  public ElasticSearchIntegrationTestRule() {
    this(DEFAULT_PROPERTIES_PATH);
  }

  public ElasticSearchIntegrationTestRule(String propertiesLocation) {
    properties = PropertyUtil.loadProperties(propertiesLocation);
  }

  private void initEsclient() {
    if (esclient == null) {
      initTransportClient();
    }
  }

  private void initObjectMapper() {
    if (objectMapper == null) {

      DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(getDateFormat());
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

  public String getDateFormat() {
    return properties.getProperty("camunda.optimize.serialization.date.format");
  }

  private void initTransportClient() {
    try {
      esclient =
          new PreBuiltTransportClient(Settings.EMPTY)
              .addTransportAddress(new TransportAddress(
                  InetAddress.getByName(properties.getProperty("camunda.optimize.es.host")),
                  Integer.parseInt(properties.getProperty("camunda.optimize.es.port"))
              ));
    } catch (Exception e) {
      logger.error("Can't connect to Elasticsearch. Please check the connection!", e);
    }
    String indexName = properties.getProperty("camunda.optimize.es.index");
    boolean exists = esclient.admin().indices()
        .prepareExists(indexName)
        .execute().actionGet().isExists();

    if (exists) {
      esclient
          .admin()
          .cluster()
          .prepareHealth(indexName)
          .setWaitForYellowStatus()
          .get();
    }
  }

  @Override
  protected void starting(Description description) {
    initObjectMapper();
    this.initEsclient();
    logger.info("Cleaning elasticsearch...");
    this.cleanAndVerify();
    logger.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
  }

  @Override
  protected void finished(Description description) {
    if (haveToClean) {
      logger.info("cleaning up elasticsearch on finish");
      this.cleanUpElasticSearch();
      this.refreshOptimizeIndexInElasticsearch();
    }
  }

  public void refreshOptimizeIndexInElasticsearch() {
    try {
      esclient.admin().indices()
          .prepareRefresh("_all")
          .get();
    } catch (IndexNotFoundException e) {
      logger.error("should not happen", e);
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
    String json = "";
    try {
      json = objectMapper.writeValueAsString(entry);
    } catch (JsonProcessingException e) {
      logger.error("Unable to add an entry to elasticsearch", e);
    }
    esclient.prepareIndex(this.getOptimizeIndex(type), type, id)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE) // necessary because otherwise I can't search for the entry immediately
      .setSource(json, XContentType.JSON)
      .get();
    addEntryToTracker(type, id);
  }

  public void addDemoUser() throws JsonProcessingException {
    CredentialsDto user = new CredentialsDto();
    user.setUsername("demo");
    user.setPassword("demo");

    System.out.println("Objectmapper" + objectMapper);

    esclient
      .prepareIndex(
        getOptimizeIndex(getUserType()),
        getUserType(),
        "1"
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.writeValueAsString(user), XContentType.JSON)
      .get();
  }

  public Integer getImportedCountOf(String elasticsearchType, ConfigurationService configurationService) {
    SearchResponse searchResponse = getClient()
      .prepareSearch(getOptimizeIndexAliasForType(elasticsearchType))
      .setTypes(elasticsearchType)
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .setFetchSource(false)
      .get();
    return Long.valueOf(searchResponse.getHits().getTotalHits()).intValue();
  }

  public Integer getActivityCount(ConfigurationService configurationService) {
    SearchResponse response = getClient()
      .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .addAggregation(
        nested(EVENTS, EVENTS)
          .subAggregation(
            count(EVENTS + "_count")
              .field(EVENTS + "." + ProcessInstanceType.EVENT_ID)
          )
      )
      .setFetchSource(false)
      .get();

    Nested nested = response.getAggregations()
      .get(EVENTS);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(EVENTS + "_count");
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

  public Integer getVariableInstanceCount(ConfigurationService configurationService) {
    SearchRequestBuilder searchRequestBuilder = getClient()
      .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(0)
      .setFetchSource(false);

    for (String variableTypeFieldLabel : VariableHelper.allVariableTypeFieldLabels) {
      searchRequestBuilder.addAggregation(
        nested(variableTypeFieldLabel, variableTypeFieldLabel)
          .subAggregation(
            count(variableTypeFieldLabel + "_count")
              .field(variableTypeFieldLabel + "." + VARIABLE_ID)
          )
      );
    }

    SearchResponse response = searchRequestBuilder.get();

    long totalVariableCount = 0L;
    for (String variableTypeFieldLabel : VariableHelper.allVariableTypeFieldLabels) {
      Nested nestedAgg = response.getAggregations().get(variableTypeFieldLabel);
      ValueCount countAggregator = nestedAgg.getAggregations()
        .get(variableTypeFieldLabel + "_count");
      totalVariableCount += countAggregator.getValue();
    }

    return Long.valueOf(totalVariableCount).intValue();
  }

  private void addEntryToTracker(String type, String id) {
    if(!documentEntriesTracker.containsKey(type)){
      List<String> idList = new LinkedList<>();
      idList.add(id);
      documentEntriesTracker.put(type, idList);
    } else {
      List<String> ids = documentEntriesTracker.get(type);
      ids.add(id);
      documentEntriesTracker.put(type, ids);
    }
  }

  public void deleteOptimizeIndexes() {
    DeleteByQueryAction.INSTANCE.newRequestBuilder(esclient)
      .refresh(true)
      .filter(matchAllQuery())
      .source("_all")
      .get();
  }

  public void cleanAndVerify() {
    cleanUpElasticSearch();
    assureElasticsearchIsClean();
  }

  private void cleanUpElasticSearch() {
    try {
      deleteOptimizeIndexes();
    } catch (Exception e) {
      //nothing to do
      logger.error("can't clean optimize indexes", e);
    }
  }

  private String getUserType() {
    return properties.getProperty("camunda.optimize.es.users.type");
  }

  private void assureElasticsearchIsClean() {
    try {
      SearchResponse response = esclient
          .prepareSearch()
          .setQuery(matchAllQuery())
          .get();
      Long hits = response.getHits().getTotalHits();
      assertThat("Elasticsearch should be clean after Test!", hits, is(0L));
    } catch (IndexNotFoundException e) {
      //nothing to do
    }
  }

  public Client getClient() {
    return esclient;
  }

  protected String getOptimizeIndex() {
    return properties.getProperty("camunda.optimize.es.index");
  }

  public String getOptimizeIndex(String type) {
    String original = this.getOptimizeIndex() + "-" + type;
    return original.toLowerCase();
  }

  public String getProcessDefinitionType() {
    return properties.getProperty("camunda.optimize.es.procdef.type");
  }

  public String getDashboardType() {
    return properties.getProperty("camunda.optimize.es.dashboard.type");
  }

  public String getProcessInstanceType() {
    return properties.getProperty("camunda.optimize.es.process.instance.type");
  }

  public String getDurationHeatmapTargetValueType() {
    return properties.getProperty("camunda.optimize.es.heatmap.duration.target.value.type");
  }

  public void disableCleanup() {
    this.haveToClean = false;
  }
}
