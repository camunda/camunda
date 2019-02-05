package org.camunda.operate.es.schema.templates;

public interface WorkflowInstanceDependant {

  String WORKFLOW_INSTANCE_ID = "workflowInstanceId";

  String getMainIndexName();

}
