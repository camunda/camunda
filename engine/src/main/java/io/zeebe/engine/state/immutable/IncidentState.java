/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.function.ObjLongConsumer;

public interface IncidentState {

  int MISSING_INCIDENT = -1;

  IncidentRecord getIncidentRecord(long incidentKey);

  long getProcessInstanceIncidentKey(long processInstanceKey);

  long getJobIncidentKey(long jobKey);

  boolean isJobIncident(IncidentRecord record);

  void forExistingProcessIncident(
      long elementInstanceKey, ObjLongConsumer<IncidentRecord> resolver);
}
