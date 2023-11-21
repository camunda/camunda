/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import java.util.function.Predicate;

public interface TestCheck extends Predicate<Object[]> {

  /* check names */
  String PROCESS_IS_DEPLOYED_CHECK = "processIsDeployedCheck";
  String PROCESS_IS_DELETED_CHECK = "processIsDeletedCheck";
  String PROCESS_INSTANCE_IS_COMPLETED_CHECK = "processInstanceIsCompletedCheck";
  String PROCESS_INSTANCE_IS_CANCELED_CHECK = "processInstanceIsCanceledCheck";

  String TASK_IS_CREATED_CHECK = "taskIsCreatedCheck";
  String TASK_IS_ASSIGNED_CHECK = "taskIsAssignedCheck";

  String TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK = "taskIsCreatedByFlowNodeBpmnIdCheck";
  String TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK = "tasksAreCreatedByFlowNodeBpmnIdCheck";
  String TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK = "taskIsCanceledByFlowNodeBpmnIdCheck";
  String TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK = "taskIsCompletedByFlowNodeBpmnIdCheck";
  String TASK_VARIABLE_EXISTS_CHECK = "taskVariableExistsCheck";
  String VARIABLES_EXIST_CHECK = "variablesExistsCheck";

  String getName();
}
