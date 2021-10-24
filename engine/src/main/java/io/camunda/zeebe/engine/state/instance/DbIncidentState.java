/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.function.ObjLongConsumer;

public final class DbIncidentState implements MutableIncidentState {

  public static final int MISSING_INCIDENT = -1;

  /** incident key -> incident record */
  private final DbLong incidentKey;

  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1916
  private final Incident incidentRead = new Incident();
  private final Incident incidentWrite = new Incident();
  private final ColumnFamily<DbLong, Incident> incidentColumnFamily;

  /** element instance key -> incident key */
  private final DbLong elementInstanceKey;

  private final ColumnFamily<DbLong, IncidentKey> processInstanceIncidentColumnFamily;

  /** job key -> incident key */
  private final DbLong jobKey;

  private final ColumnFamily<DbLong, IncidentKey> jobIncidentColumnFamily;
  private final IncidentKey incidentKeyValue = new IncidentKey();

  private final IncidentMetrics metrics;

  public DbIncidentState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final int partitionId) {
    incidentKey = new DbLong();
    incidentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.INCIDENTS, transactionContext, incidentKey, incidentRead);

    elementInstanceKey = new DbLong();
    processInstanceIncidentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.INCIDENT_PROCESS_INSTANCES,
            transactionContext,
            elementInstanceKey,
            incidentKeyValue);

    jobKey = new DbLong();
    jobIncidentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.INCIDENT_JOBS, transactionContext, jobKey, incidentKeyValue);

    metrics = new IncidentMetrics(partitionId);
  }

  @Override
  public synchronized void createIncident(final long incidentKey, final IncidentRecord incident) {
    this.incidentKey.wrapLong(incidentKey);
    incidentWrite.setRecord(incident);
    incidentColumnFamily.put(this.incidentKey, incidentWrite);

    incidentKeyValue.set(incidentKey);
    if (isJobIncident(incident)) {
      jobKey.wrapLong(incident.getJobKey());
      jobIncidentColumnFamily.put(jobKey, incidentKeyValue);
    } else {
      elementInstanceKey.wrapLong(incident.getElementInstanceKey());
      processInstanceIncidentColumnFamily.put(elementInstanceKey, incidentKeyValue);
    }

    metrics.incidentCreated();
  }

  @Override
  public synchronized void deleteIncident(final long key) {
    final IncidentRecord incidentRecord = getIncidentRecord(key);

    if (incidentRecord != null) {
      incidentColumnFamily.delete(incidentKey);

      if (isJobIncident(incidentRecord)) {
        jobKey.wrapLong(incidentRecord.getJobKey());
        jobIncidentColumnFamily.delete(jobKey);
      } else {
        elementInstanceKey.wrapLong(incidentRecord.getElementInstanceKey());
        processInstanceIncidentColumnFamily.delete(elementInstanceKey);
      }

      metrics.incidentResolved();
    }
  }

  @Override
  public synchronized IncidentRecord getIncidentRecord(final long incidentKey) {
    this.incidentKey.wrapLong(incidentKey);

    final Incident incident = incidentColumnFamily.get(this.incidentKey);
    if (incident != null) {
      return incident.getRecord();
    }
    return null;
  }

  @Override
  public synchronized long getProcessInstanceIncidentKey(final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);

    final IncidentKey incidentKey = processInstanceIncidentColumnFamily.get(elementInstanceKey);

    if (incidentKey != null) {
      return incidentKey.get();
    }

    return MISSING_INCIDENT;
  }

  @Override
  public synchronized long getJobIncidentKey(final long jobKey) {
    this.jobKey.wrapLong(jobKey);
    final IncidentKey incidentKey = jobIncidentColumnFamily.get(this.jobKey);

    if (incidentKey != null) {
      return incidentKey.get();
    }
    return MISSING_INCIDENT;
  }

  @Override
  public synchronized boolean isJobIncident(final IncidentRecord record) {
    return record.getJobKey() > 0;
  }

  @Override
  public synchronized void forExistingProcessIncident(
      final long elementInstanceKey, final ObjLongConsumer<IncidentRecord> resolver) {
    final long processIncidentKey = getProcessInstanceIncidentKey(elementInstanceKey);

    final boolean hasIncident = processIncidentKey != IncidentState.MISSING_INCIDENT;
    if (hasIncident) {
      final IncidentRecord incidentRecord = getIncidentRecord(processIncidentKey);
      resolver.accept(incidentRecord, processIncidentKey);
    }
  }
}
