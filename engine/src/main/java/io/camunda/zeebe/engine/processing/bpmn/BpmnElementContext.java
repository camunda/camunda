/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

/** Process instance-related data of the element that is executed. */
public interface BpmnElementContext {

  long getElementInstanceKey();

  long getFlowScopeKey();

  long getProcessInstanceKey();

  long getParentProcessInstanceKey();

  long getParentElementInstanceKey();

  long getProcessDefinitionKey();

  int getProcessVersion();

  DirectBuffer getBpmnProcessId();

  DirectBuffer getElementId();

  BpmnElementType getBpmnElementType();

  // TODO (saig0): use an immutable interface for the record value (#6800)
  /**
   * Caution! Don't modify the value to avoid unexpected side-effects.
   *
   * @return the value of the record that is currently processed
   */
  ProcessInstanceRecord getRecordValue();

  ProcessInstanceIntent getIntent();

  BpmnElementContext copy(
      long elementInstanceKey, ProcessInstanceRecord recordValue, ProcessInstanceIntent intent);
}
