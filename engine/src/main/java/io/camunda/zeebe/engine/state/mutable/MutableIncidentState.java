/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.IncidentState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;

public interface MutableIncidentState extends IncidentState {

  void createIncident(long incidentKey, IncidentRecord incident);

  void deleteIncident(long key);
}
