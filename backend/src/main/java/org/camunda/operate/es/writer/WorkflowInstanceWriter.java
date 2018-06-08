package org.camunda.operate.es.writer;

import java.util.List;
import org.camunda.operate.po.WorkflowInstanceEntity;

/**
 * @author Svetlana Dorokhova.
 */
public interface WorkflowInstanceWriter {
  void persistWorkflowInstances(List<WorkflowInstanceEntity> workflowInstances);
}
