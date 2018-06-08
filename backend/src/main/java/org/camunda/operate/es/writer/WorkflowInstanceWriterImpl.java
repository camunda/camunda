package org.camunda.operate.es.writer;

import java.time.format.DateTimeFormatter;
import java.util.List;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.po.WorkflowInstanceEntity;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("elasticsearch")
public class WorkflowInstanceWriterImpl extends AbstractWriter implements WorkflowInstanceWriter {

  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceWriterImpl.class);

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Override
  public void persistWorkflowInstances(List<WorkflowInstanceEntity> workflowInstances){
    try {
      logger.debug("Writing [{}] workflow instances to elasticsearch", workflowInstances.size());
      BulkRequestBuilder bulkRequest = esClient.prepareBulk();
      for (WorkflowInstanceEntity workflowInstance : workflowInstances) {
        addWorkflowInstance2PersistRequest(bulkRequest, workflowInstance);
      }
      processBulkRequest(bulkRequest);
    } catch (Exception ex) {
      logger.error("Error while persisting workflow instances", ex);
      //TODO
    }
  }

  private void addWorkflowInstance2PersistRequest(BulkRequestBuilder bulkRequest, WorkflowInstanceEntity workflowInstance) throws JsonProcessingException {
    bulkRequest.add(esClient
      .prepareIndex(
        WorkflowInstanceType.TYPE,
        WorkflowInstanceType.TYPE,
        workflowInstance.getId())
      .setSource(objectMapper.writeValueAsString(workflowInstance), XContentType.JSON)
    );

  }

}
