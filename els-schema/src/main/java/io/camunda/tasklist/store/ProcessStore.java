/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.ProcessEntity;
import java.util.List;

public interface ProcessStore {
  ProcessEntity getProcessByProcessDefinitionKey(String processDefinitionKey);

  ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId);

  ProcessEntity getProcessByBpmnProcessId(final String bpmnProcessId, final String tenantId);

  ProcessEntity getProcess(String processId);

  List<ProcessEntity> getProcesses(final List<String> processDefinitions, final String tenantId);

  List<ProcessEntity> getProcesses(
      String search, final List<String> processDefinitions, final String tenantId);

  List<ProcessEntity> getProcessesStartedByForm();
}
