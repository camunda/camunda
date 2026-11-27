/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;

public interface MutableIncidentState extends IncidentState {

  void createIncident(long incidentKey, IncidentRecord incident);

  void deleteIncident(long key);

  void migrateIncident(long incidentKey, IncidentRecord incident);

  /** Adds 1 to the active incident count, the entry is created with 1 when it is missing. */
  void incrementActiveIncidentCount(long elementInstanceKey);

  /**
   * Subtracts 1 from the active incident count and deletes the entry when it reaches 0. Does
   * nothing when there is no entry, for example for incidents created before the count existed.
   */
  void decrementActiveIncidentCount(long elementInstanceKey);
}
