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
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;

public final class JobCreatedProcessor implements TypedRecordProcessor<JobRecord> {

  private final WorkflowState workflowState;

  public JobCreatedProcessor(WorkflowState scopeInstances) {
    this.workflowState = scopeInstances;
  }

  @Override
  public void processRecord(
      TypedRecord<JobRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final long elementInstanceKey = record.getValue().getElementInstanceKey();
    if (elementInstanceKey > 0) {
      final ElementInstanceState elementInstanceState = workflowState.getElementInstanceState();
      final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

      if (elementInstance != null) {
        elementInstance.setJobKey(record.getKey());
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }
}
