package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.Map;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.types.WorkflowType;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.es.types.WorkflowType.BPMN_XML;

@Component
public class WorkflowReader {

  private Logger logger = LoggerFactory.getLogger(WorkflowReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowType workflowType;

  /**
   * Gets the workflow diagram XML as a string.
   * @param workflowId
   * @return
   */
  public String getDiagram(String workflowId) {
    GetResponse response = esClient.prepareGet(workflowType.getType(), workflowType.getType(), workflowId).setFetchSource(BPMN_XML, null).get();

    if (response.isExists()) {
      Map<String, Object> result = response.getSourceAsMap();
      return (String) result.get(BPMN_XML);
    }
    else {
      throw new NotFoundException(String.format("Could not find xml for workflow with id '%s'.", workflowId));
    }
  }

  /**
   * Gets the workflow by id.
   * @param workflowId
   * @return
   */
  public WorkflowEntity getWorkflow(String workflowId) {
    final GetResponse response = esClient.prepareGet(workflowType.getType(), workflowType.getType(), workflowId).get();

    if (response.isExists()) {
      return fromSearchHit(response.getSourceAsString());
    }
    else {
      throw new NotFoundException(String.format("Could not find workflow with id '%s'.", workflowId));
    }
  }

  private WorkflowEntity fromSearchHit(String workflowString) {
    WorkflowEntity workflow = null;
    try {
      workflow = objectMapper.readValue(workflowString, WorkflowEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading workflow from Elasticsearch!", e);
      throw new RuntimeException("Error while reading workflow from Elasticsearch!", e);
    }
    return workflow;
  }

}
