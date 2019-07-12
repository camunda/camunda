/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.incident;

import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.BpmnStepProcessor;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.IncidentIntent;

public class IncidentEventProcessors {

  public static void addProcessors(
      TypedRecordProcessors typedRecordProcessors,
      ZeebeState zeebeState,
      BpmnStepProcessor bpmnStepProcessor) {
    typedRecordProcessors
        .onCommand(
            ValueType.INCIDENT, IncidentIntent.CREATE, new CreateIncidentProcessor(zeebeState))
        .onCommand(
            ValueType.INCIDENT,
            IncidentIntent.RESOLVE,
            new ResolveIncidentProcessor(bpmnStepProcessor, zeebeState));
  }
}
