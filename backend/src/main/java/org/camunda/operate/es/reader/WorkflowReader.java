package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.Map;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.types.WorkflowType;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.es.types.WorkflowType.BPMN_XML;
import static org.camunda.operate.es.types.WorkflowType.ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@Profile("elasticsearch")
public class WorkflowReader {

  private Logger logger = LoggerFactory.getLogger(WorkflowReader.class);

  @Autowired
  protected TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  public String getDiagram(String workflowId) {
    BoolQueryBuilder query = boolQuery().must(termQuery(ID, workflowId));

    SearchResponse response = esClient
        .prepareSearch(WorkflowType.TYPE)
        .setQuery(query)
        .setFetchSource(BPMN_XML, null)
        .setSize(1)
        .get();

    if (response.getHits().getTotalHits() > 0) {
      SearchHit hit = response.getHits().getAt(0);
      Map<String, Object> result = hit.getSourceAsMap();
      return (String) result.get(BPMN_XML);
    }
    else {
      throw new NotFoundException(String.format("Could not find xml for workflow with id '%s'.", workflowId));
    }
  }

  public WorkflowEntity getWorkflowById(String workflowId) {
    final GetResponse getResponse = esClient.prepareGet(WorkflowType.TYPE, WorkflowType.TYPE, workflowId).get();
    return fromSearchHit(getResponse.getSourceAsString());
  }

  private WorkflowEntity fromSearchHit(String workflowString) {
    WorkflowEntity workflow = null;
    try {
      workflow = objectMapper.readValue(workflowString, WorkflowEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading workflow from elastic search!", e);
    }
    return workflow;
  }

}
