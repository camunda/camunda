/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public interface BpmnElementContext {

  long getElementInstanceKey();

  long getFlowScopeKey();

  long getWorkflowInstanceKey();

  long getWorkflowKey();

  int getWorkflowVersion();

  DirectBuffer getBpmnProcessId();

  DirectBuffer getElementId();

  BpmnElementType getBpmnElementType();

  // ---- for migration ----

  <T extends ExecutableFlowElement> BpmnStepContext<T> toStepContext();

  WorkflowInstanceRecord getRecordValue();

  WorkflowInstanceIntent getIntent();

  BpmnElementContext copy(
      long elementInstanceKey, WorkflowInstanceRecord recordValue, WorkflowInstanceIntent intent);
}
