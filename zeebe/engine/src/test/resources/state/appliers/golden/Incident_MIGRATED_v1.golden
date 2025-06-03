/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;

final class IncidentMigratedApplier implements TypedEventApplier<IncidentIntent, IncidentRecord> {
  private final MutableIncidentState incidentState;

  public IncidentMigratedApplier(final MutableIncidentState incidentState) {
    this.incidentState = incidentState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    incidentState.migrateIncident(incidentKey, value);
  }
}
