/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class JobCompletedEventProcessor implements TypedRecordProcessor<JobRecord> {

  private final WorkflowState workflowState;

  public JobCompletedEventProcessor(final WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      final TypedRecord<JobRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final JobRecord jobEvent = record.getValue();
    final long elementInstanceKey = jobEvent.getElementInstanceKey();
    final ElementInstance elementInstance =
        workflowState.getElementInstanceState().getInstance(elementInstanceKey);

    if (elementInstance != null) {
      final long scopeKey = elementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance =
          workflowState.getElementInstanceState().getInstance(scopeKey);

      if (scopeInstance.isActive()) {
        final WorkflowInstanceRecord value = elementInstance.getValue();

        streamWriter.appendFollowUpEvent(
            elementInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, value);
        elementInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
        elementInstance.setJobKey(-1);
        elementInstance.setValue(value);
        workflowState.getElementInstanceState().updateInstance(elementInstance);

        workflowState.getEventScopeInstanceState().shutdownInstance(elementInstanceKey);
        workflowState
            .getElementInstanceState()
            .getVariablesState()
            .setTemporaryVariables(elementInstanceKey, jobEvent.getVariablesBuffer());
      }
    }
  }
}
