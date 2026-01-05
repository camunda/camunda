/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.writer;

import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.Map;

public record ProcessInstanceSource(
    Long processInstanceKey,
    Long processDefinitionKey,
    String bpmnProcessId,
    Long rootProcessInstanceKey) {

  public static ProcessInstanceSource fromSourceMap(final Map<String, Object> sourceMap) {
    return new ProcessInstanceSource(
        (Long) sourceMap.get(OperationTemplate.PROCESS_INSTANCE_KEY),
        (Long) sourceMap.get(OperationTemplate.PROCESS_DEFINITION_KEY),
        (String) sourceMap.get(OperationTemplate.BPMN_PROCESS_ID),
        (Long) sourceMap.get(OperationTemplate.ROOT_PROCESS_INSTANCE_KEY));
  }

  public static ProcessInstanceSource formProcessInstanceForListViewEntity(
      final ProcessInstanceForListViewEntity source) {
    return new ProcessInstanceSource(
        source.getProcessInstanceKey(),
        source.getProcessDefinitionKey(),
        source.getBpmnProcessId(),
        source.getRootProcessInstanceKey());
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }
}
