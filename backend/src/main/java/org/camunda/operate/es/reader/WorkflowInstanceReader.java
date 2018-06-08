package org.camunda.operate.es.reader;

import java.util.List;
import org.camunda.operate.po.WorkflowInstanceEntity;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;

/**
 * @author Svetlana Dorokhova.
 */
public interface WorkflowInstanceReader {

  long queryWorkflowInstancesCount(WorkflowInstanceQueryDto workflowInstanceQuery);

  List<WorkflowInstanceEntity> queryWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery);

  WorkflowInstanceEntity getWorkflowInstanceById(String workflowInstanceId);

}
