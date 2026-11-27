/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the keys whose incident flag changes into an incident event. A key on the element instance
 * path flips to true when its active incident count is 0 before a CREATED event, and back to false
 * when its count is 1 before a RESOLVED event. Exporters cannot read engine state, so the processor
 * decides and the event carries the decision.
 */
public final class IncidentFlagKeys {

  private IncidentFlagKeys() {}

  public static void setForCreated(
      final IncidentState incidentState, final IncidentRecord incident) {
    set(incidentState, incident, 0);
  }

  public static void setForResolved(
      final IncidentState incidentState, final IncidentRecord incident) {
    set(incidentState, incident, 1);
  }

  private static void set(
      final IncidentState incidentState, final IncidentRecord incident, final int flipCount) {
    final List<Long> flagKeys = new ArrayList<>();
    for (final List<Long> processInstancePath : incident.getElementInstancePath()) {
      for (final long key : processInstancePath) {
        if (incidentState.getActiveIncidentCount(key) == flipCount) {
          flagKeys.add(key);
        }
      }
    }
    incident.setIncidentFlagKeys(flagKeys);
  }
}
