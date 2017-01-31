package org.camunda.optimize.test.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElasticSearchIntegrationTestRule extends TestWatcher {

  private static ElasticSearchIntegrationTestRule rule;
  private TransportClient esclient;
  private Properties properties;
  private ObjectMapper objectMapper = new ObjectMapper();
  private Logger logger = LoggerFactory.getLogger(ElasticSearchIntegrationTestRule.class);

  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  private final String CUSTOM_ANALYZER_NAME = "case_sensitive";


  public static ElasticSearchIntegrationTestRule getInstance() {
    if (rule == null) {
      rule = new ElasticSearchIntegrationTestRule();
    }
    return rule;
  }

  private ElasticSearchIntegrationTestRule() {
    loadProperties();
    startEsclient();
    createOptimizeIndex();
    createMappings();
  }

  private void createMappings() {
    addMapping(getEventType(), "activityId");
  }

  private void addMapping(String type, String field) {
    XContentBuilder content = null;
    try {
      content = jsonBuilder()
        .startObject()
          .startObject("properties")
            .startObject(field)
              .field("type", "text")
              .field("fielddata", true)
              .field("analyzer", CUSTOM_ANALYZER_NAME)
            .endObject()
          .endObject()
        .endObject();
    } catch (IOException e) {
      String message = "Could not add mapping to the index '" + getOptimizeIndex() +
        "' , type '" + type +
        "' and field '" + field + "'!";
      logger.error(message, e);
    }
    esclient
      .admin()
        .indices()
          .preparePutMapping(getOptimizeIndex())
          .setType(type)
          .setSource(content)
      .get();
  }

  private void createOptimizeIndex() {
    Settings indexSettings = null;
    try {
      indexSettings = buildSettings();
    } catch (IOException e) {
      e.printStackTrace();
    }
    CreateIndexRequest indexRequest = new CreateIndexRequest("optimize", indexSettings);
    esclient.admin().indices().create(indexRequest).actionGet();
  }

  private void startEsclient() {
    String address = properties.getProperty("camunda.optimize.es.host");
    int port = Integer.parseInt(properties.getProperty("camunda.optimize.es.port"));
    try {
      esclient = new PreBuiltTransportClient(Settings.EMPTY)
        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(address), port));
    } catch (UnknownHostException e) {
      logger.error("Could not create connection to the test elasticsearch server!", e);
    }
  }

  private Settings buildSettings() throws IOException {
    return Settings.builder().loadFromSource(jsonBuilder()
      .startObject()
        .startObject("analysis")
          .startObject("analyzer")
            .startObject(CUSTOM_ANALYZER_NAME)
              .field("type", "custom")
              .field("tokenizer", "whitespace")
              .field("filter", new String[]{"standard"})
            .endObject()
          .endObject()
        .endObject()
      .endObject().string()).build();
  }

  private void loadProperties() {
    properties = new Properties();
    try {
      properties.load(
        ElasticSearchIntegrationTestRule.class
          .getClassLoader()
          .getResourceAsStream("service.properties")
      );
    } catch (IOException ex) {
      logger.error("Unable to load test properties!", ex);
    }
  }

  private String getOptimizeIndex() {
    return properties.getProperty("camunda.optimize.es.index");
  }

  private String getEventType() {
    return properties.getProperty("camunda.optimize.es.event.type");
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
    esclient.prepareIndex(getOptimizeIndex(), type, id)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE) // necessary because otherwise I can't search for the entry immediately
      .setSource(json).get();
    addEntryToTracker(type, id);
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

  @Override
  protected void finished(Description description) {
    cleanUpElasticSearch();
    assureElasticsearchIsClean();
  }

  private void cleanUpElasticSearch() {
    for(Map.Entry<String, List<String>> entry: documentEntriesTracker.entrySet()) {
      String type = entry.getKey();
      List<String> ids = entry.getValue();
      for(String id: ids) {
        esclient.prepareDelete(getOptimizeIndex(), type, id)
          .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
          .get();
      }
    }

  }

  private void assureElasticsearchIsClean() {
    String[] types = {};
    types = documentEntriesTracker.keySet().toArray(types);
    SearchResponse response = esclient.prepareSearch(getOptimizeIndex())
      .setTypes(types)
      .setQuery(QueryBuilders.matchAllQuery())
      .get();

    Long hits = response.getHits().getTotalHits();
    assertThat("Elasticsearch should be clean after Test!", hits, is(0L));
  }
}
