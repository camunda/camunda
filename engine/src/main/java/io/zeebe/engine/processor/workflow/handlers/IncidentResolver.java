/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.state.instance.IncidentState;

public class IncidentResolver {
  private final IncidentState incidentState;

  public IncidentResolver(IncidentState incidentState) {
    this.incidentState = incidentState;
  }

  public void resolveIncidents(BpmnStepContext context) {
    resolveIncidents(context, context.getKey());
  }

  public void resolveIncidents(BpmnStepContext context, long scopeKey) {
    incidentState.forExistingWorkflowIncident(
        scopeKey, (record, key) -> context.getOutput().appendResolvedIncidentEvent(key, record));
  }
}
