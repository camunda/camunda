package org.camunda.operate.es.reader;

import java.util.List;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;

/**
 * @author Svetlana Dorokhova.
 */
public interface WorkflowInstanceReader {

  long countWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery);

  List<WorkflowInstanceEntity> queryWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery, Integer firstResult, Integer maxResults);

  WorkflowInstanceEntity getWorkflowInstanceById(String workflowInstanceId);

}
