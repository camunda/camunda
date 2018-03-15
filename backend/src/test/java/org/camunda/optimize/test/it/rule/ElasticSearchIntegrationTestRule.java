package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.service.util.CustomDeserializer;
import org.camunda.optimize.service.util.CustomSerializer;
import org.camunda.optimize.test.util.PropertyUtil;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryResponse;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
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

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElasticSearchIntegrationTestRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(ElasticSearchIntegrationTestRule.class);
  private Properties properties;
  private static ObjectMapper objectMapper;
  private static Client esclient;
  private boolean haveToClean = true;

  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  public ElasticSearchIntegrationTestRule() {
    properties = PropertyUtil.loadProperties("integration-rules.properties");
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


  @Override
  protected void failed(Throwable e, Description description) {
    logger.info("test failure detected, preparing ES dump");

    String repositoryName = description.getClassName().toLowerCase();

    Settings settings = Settings.builder()
        .put("location", "./")
        .put("compress", false).build();

    PutRepositoryResponse putRepositoryResponse = esclient.admin().cluster()
        .preparePutRepository(repositoryName)
        .setType("fs").setSettings(settings).get();

    logger.info("created repository [{}]", repositoryName);


    String snapshotName = description.getMethodName().toLowerCase();
    logger.info("creating snapshot [{}]", snapshotName);
    CreateSnapshotResponse createSnapshotResponse = esclient
        .admin().cluster()
        .prepareCreateSnapshot(repositoryName, snapshotName.toLowerCase())
        .setWaitForCompletion(true)
        .setIndices(properties.getProperty("camunda.optimize.es.index"))
        .get();

    int status = createSnapshotResponse.status().getStatus();
    if (status == 200) {
      logger.info("Snapshot was created");
    } else {
      logger.info("Snapshot return code [{}]", status);
    }
  }


  public void refreshOptimizeIndexInElasticsearch() {
    try {
      esclient.admin().indices()
          .prepareRefresh()
          .get();
    } catch (IndexNotFoundException e) {
      //nothing to do
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

  public void deleteOptimizeIndex() {
    GetIndexResponse indexes = esclient.admin().indices().prepareGetIndex().get();
    for (String indexName : indexes.getIndices()) {
      DeleteByQueryAction.INSTANCE.newRequestBuilder(esclient)
        .refresh(true)
        .filter(matchAllQuery())
        .source(indexName)
        .execute()
        .actionGet();
    }
  }

  public void cleanAndVerify() {
    cleanUpElasticSearch();
    assureElasticsearchIsClean();
  }

  private void cleanUpElasticSearch() {
    GetIndexResponse indexes = esclient.admin().indices().prepareGetIndex().get();
    for (String indexName : indexes.getIndices()) {
      try {
        DeleteByQueryAction.INSTANCE.newRequestBuilder(esclient)
          .refresh(true)
          .filter(matchAllQuery())
          .source(indexName)
          .execute()
          .actionGet();
      } catch (Exception e) {
        //nothing to do
        logger.error("can't clean index [{}]", indexName, e);
      }
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

  public String getReportType() {
    return properties.getProperty("camunda.optimize.es.report.type");
  }

  public String getDashboardType() {
    return properties.getProperty("camunda.optimize.es.dashboard.type");
  }

  public String getProcessDefinitionXmlType() {
    return properties.getProperty("camunda.optimize.es.procdef.xml.type");
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
