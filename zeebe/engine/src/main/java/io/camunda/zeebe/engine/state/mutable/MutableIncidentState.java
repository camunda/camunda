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
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;

public interface MutableIncidentState extends IncidentState, StreamProcessorLifecycleAware {

  void createIncident(long incidentKey, IncidentRecord incident);

  void deleteIncident(long key);

  void migrateIncident(long incidentKey, IncidentRecord incident);
}
