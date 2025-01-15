/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.writer;

import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import java.util.Map;
import java.util.Objects;

public class ProcessInstanceSource {
  private Long processInstanceKey;
  private Long processDefinitionKey;
  private String bpmnProcessId;

  public static ProcessInstanceSource fromSourceMap(final Map<String, Object> sourceMap) {
    final ProcessInstanceSource processInstanceSource = new ProcessInstanceSource();
    processInstanceSource.setProcessInstanceKey(
        (Long) sourceMap.get(OperationTemplate.PROCESS_INSTANCE_KEY));
    processInstanceSource.setProcessDefinitionKey(
        (Long) sourceMap.get(OperationTemplate.PROCESS_DEFINITION_KEY));
    processInstanceSource.setBpmnProcessId(
        (String) sourceMap.get(OperationTemplate.BPMN_PROCESS_ID));
    return processInstanceSource;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ProcessInstanceSource setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceSource setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstanceSource setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processInstanceKey, processDefinitionKey, bpmnProcessId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessInstanceSource that = (ProcessInstanceSource) o;
    return Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId);
  }
}
