package org.camunda.optimize.test.rule;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
public class ElasticSearchIntegrationTestRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(ElasticSearchIntegrationTestRule.class);

  @Autowired
  private TransportClient esclient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ElasticSearchSchemaInitializer schemaInitializer;

  // maps types to a list of document entry ids added to that type
  private Map<String, List<String>> documentEntriesTracker = new HashMap<>();

  @Override
  protected void starting(Description description) {
    logger.info("Initializing elastic search schema...");
    this.cleanAndVerify();
    schemaInitializer.initializeSchema();
    logger.info("Schema has been successfully initialized!");
  }

  public void refreshOptimizeIndexInElasticsearch() {
    esclient.admin().indices()
        .prepareRefresh(configurationService.getOptimizeIndex())
        .get();
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
    esclient.prepareIndex(configurationService.getOptimizeIndex(), type, id)
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
    cleanAndVerify();
  }

  public void cleanAndVerify() {
    cleanUpElasticSearch();
    assureElasticsearchIsClean();
  }

  private void cleanUpElasticSearch() {
    ImmutableOpenMap<String, IndexMetaData> indices = esclient.admin().cluster()
        .prepareState().execute()
        .actionGet().getState()
        .getMetaData().indices();

    for (ObjectCursor<IndexMetaData> indexMeta : indices.values()) {

      DeleteIndexResponse delete = esclient
          .admin().indices()
          .delete(new DeleteIndexRequest(indexMeta.value.getIndex().getName()))
          .actionGet();
      if (!delete.isAcknowledged()) {
        logger.error("Index wasn't deleted");
      }
    }
    esclient.admin().indices().prepareRefresh().get();
  }

  private void assureElasticsearchIsClean() {
    IndicesExistsResponse response = esclient
      .admin()
      .indices()
      .prepareExists(configurationService.getOptimizeIndex())
      .get();
    if(response.isExists()){
      throw new OptimizeIntegrationTestException("Elasticsearch indices should be clean!");
    } else {
      logger.info("Optimize index is not found, as expected. Elasticsearch is clean and ready for next test!");
    }
  }
}
