/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store;

import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.List;

public interface ProcessStore {
  ProcessEntity getProcessByProcessDefinitionKey(String processDefinitionKey);

  ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId);

  ProcessEntity getProcessByBpmnProcessId(final String bpmnProcessId, final String tenantId);

  ProcessEntity getProcess(String processId);

  List<ProcessEntity> getProcesses(
      final List<String> processDefinitions, final String tenantId, final Boolean isStartedByForm);

  List<ProcessEntity> getProcesses(
      String search,
      final List<String> processDefinitions,
      final String tenantId,
      final Boolean isStartedByForm);

  List<ProcessEntity> getProcessesStartedByForm();
}
