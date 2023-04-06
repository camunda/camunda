/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;

public final class IncidentEventProcessors {

  public static void addProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    typedRecordProcessors.onCommand(
        ValueType.INCIDENT,
        IncidentIntent.RESOLVE,
        new ResolveIncidentProcessor(
            processingState, bpmnStreamProcessor, writers, jobActivationBehavior));
  }
}
