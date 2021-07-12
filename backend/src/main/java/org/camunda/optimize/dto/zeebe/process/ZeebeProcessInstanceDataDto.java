/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.zeebe.process;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class ZeebeProcessInstanceDataDto implements ProcessInstanceRecordValue {

  private int version;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private long flowScopeKey;
  private BpmnElementType bpmnElementType;
  private long parentProcessInstanceKey;
  private long parentElementInstanceKey;
  private String elementId;
  private long processInstanceKey;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }
}
