package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.test.util.PropertyUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
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
  private ObjectMapper objectMapper;
  private Properties properties;
  private Client esclient;

  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  public ElasticSearchIntegrationTestRule() {
    properties = PropertyUtil.loadProperties("it/it-test.properties");
  }

  private void init() {
    initObjectMapper();
    initTransport();
  }

  private void initObjectMapper() {
    objectMapper = new ObjectMapper();
    objectMapper.setDateFormat(new SimpleDateFormat(getDateFormat()));
  }

  public String getDateFormat() {
    return properties.getProperty("camunda.optimize.serialization.date.format");
  }

  private void initTransport() {
    try {
      esclient =
          new PreBuiltTransportClient(Settings.EMPTY)
              .addTransportAddress(new InetSocketTransportAddress(
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
    if (esclient == null) {
      this.init();
    }
    logger.info("Cleaning elasticsearch...");
    this.cleanAndVerify();
    logger.info("All documents have been wiped out! Elasticsearch has successfully been cleaned!");
  }

  @Override
  protected void finished(Description description) {
    logger.info("cleaning up elasticsearch on finish");
    this.cleanUpElasticSearch();
    this.refreshOptimizeIndexInElasticsearch();
  }

  public void refreshOptimizeIndexInElasticsearch() {
    try {
      esclient.admin().indices()
          .prepareRefresh(this.getOptimizeIndex())
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
    esclient.prepareIndex(this.getOptimizeIndex(), type, id)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE) // necessary because otherwise I can't search for the entry immediately
      .setSource(json, XContentType.JSON)
      .get();
    addEntryToTracker(type, id);
  }

  public void addDemoUser() throws JsonProcessingException {
    CredentialsDto user = new CredentialsDto();
    user.setUsername("demo");
    user.setPassword("demo");

    esclient
      .prepareIndex(
        getOptimizeIndex(),
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

  public void cleanAndVerify() {
    cleanUpElasticSearch();
    assureElasticsearchIsClean();
  }

  private void cleanUpElasticSearch() {
    String indexName = properties.getProperty("camunda.optimize.es.index");
    boolean exists = esclient.admin().indices()
        .prepareExists(indexName)
        .execute().actionGet().isExists();
    esclient.admin().indices().prepareFlush(getOptimizeIndex()).get();

    if (exists) {
      DeleteByQueryAction.INSTANCE.newRequestBuilder(esclient)
          .refresh(true)
          .filter(matchAllQuery())
          .source(indexName)
          .execute()
          .actionGet();
    }
  }

  private String getUserType() {
    return properties.getProperty("camunda.optimize.es.users.type");
  }

  private void assureElasticsearchIsClean() {
    try {
      SearchResponse response = esclient
          .prepareSearch(this.getOptimizeIndex())
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

  public String getOptimizeIndex() {
    return properties.getProperty("camunda.optimize.es.index");
  }

  public String getProcessDefinitionType() {
    return properties.getProperty("camunda.optimize.es.procdef.type");
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
}
