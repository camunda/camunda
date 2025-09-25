/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.function.ObjLongConsumer;

public interface IncidentState {

  int MISSING_INCIDENT = -1;

  IncidentRecord getIncidentRecord(long incidentKey);

  IncidentRecord getIncidentRecord(long incidentKey, final AuthorizedTenants authorizations);

  long getProcessInstanceIncidentKey(long processInstanceKey);

  long getJobIncidentKey(long jobKey);

  boolean isJobIncident(IncidentRecord record);

  void forExistingProcessIncident(
      long elementInstanceKey, ObjLongConsumer<IncidentRecord> resolver);
}
