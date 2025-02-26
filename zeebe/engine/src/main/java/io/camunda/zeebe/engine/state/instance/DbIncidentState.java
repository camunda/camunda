/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.List;
import java.util.Map;
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
  private final DbForeignKey<DbLong> elementInstanceKey;

  private final ColumnFamily<DbForeignKey<DbLong>, IncidentKey> processInstanceIncidentColumnFamily;

  /** job key -> incident key */
  private final DbForeignKey<DbLong> jobKey;

  private final ColumnFamily<DbForeignKey<DbLong>, IncidentKey> jobIncidentColumnFamily;
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

    elementInstanceKey = new DbForeignKey<>(new DbLong(), ZbColumnFamilies.ELEMENT_INSTANCE_KEY);
    processInstanceIncidentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.INCIDENT_PROCESS_INSTANCES,
            transactionContext,
            elementInstanceKey,
            incidentKeyValue);

    jobKey = new DbForeignKey<>(new DbLong(), ZbColumnFamilies.JOBS);
    jobIncidentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.INCIDENT_JOBS, transactionContext, jobKey, incidentKeyValue);

    metrics = new IncidentMetrics(zeebeDb.getMeterRegistry());
  }

  @Override
  public void createIncident(final long incidentKey, final IncidentRecord incident) {
    this.incidentKey.wrapLong(incidentKey);
    incidentWrite.setRecord(incident);
    incidentColumnFamily.insert(this.incidentKey, incidentWrite);

    incidentKeyValue.set(incidentKey);
    if (isJobIncident(incident)) {
      jobKey.inner().wrapLong(incident.getJobKey());
      jobIncidentColumnFamily.insert(jobKey, incidentKeyValue);
    } else {
      elementInstanceKey.inner().wrapLong(incident.getElementInstanceKey());
      processInstanceIncidentColumnFamily.insert(elementInstanceKey, incidentKeyValue);
    }

    metrics.incidentCreated();
  }

  @Override
  public void deleteIncident(final long key) {
    final IncidentRecord incidentRecord = getIncidentRecord(key);

    if (incidentRecord != null) {
      incidentColumnFamily.deleteExisting(incidentKey);

      if (isJobIncident(incidentRecord)) {
        jobKey.inner().wrapLong(incidentRecord.getJobKey());
        jobIncidentColumnFamily.deleteExisting(jobKey);
      } else {
        elementInstanceKey.inner().wrapLong(incidentRecord.getElementInstanceKey());
        processInstanceIncidentColumnFamily.deleteExisting(elementInstanceKey);
      }

      metrics.incidentResolved();
    }
  }

  @Override
  public void migrateIncident(final long incidentKey, final IncidentRecord incident) {
    this.incidentKey.wrapLong(incidentKey);
    incidentWrite.setRecord(incident);
    incidentColumnFamily.update(this.incidentKey, incidentWrite);
  }

  @Override
  public IncidentRecord getIncidentRecord(final long incidentKey) {
    this.incidentKey.wrapLong(incidentKey);

    final Incident incident = incidentColumnFamily.get(this.incidentKey);
    if (incident != null) {
      return incident.getRecord();
    }
    return null;
  }

  @Override
  public IncidentRecord getIncidentRecord(
      final long incidentKey, final Map<String, Object> authorizations) {
    final IncidentRecord incident = getIncidentRecord(incidentKey);
    if (incident != null
        && getAuthorizedTenantIds(authorizations).contains(incident.getTenantId())) {
      return incident;
    }
    return null;
  }

  @Override
  public long getProcessInstanceIncidentKey(final long processInstanceKey) {
    elementInstanceKey.inner().wrapLong(processInstanceKey);

    final IncidentKey incidentKey = processInstanceIncidentColumnFamily.get(elementInstanceKey);

    if (incidentKey != null) {
      return incidentKey.get();
    }

    return MISSING_INCIDENT;
  }

  @Override
  public long getJobIncidentKey(final long jobKey) {
    this.jobKey.inner().wrapLong(jobKey);
    final IncidentKey incidentKey = jobIncidentColumnFamily.get(this.jobKey);

    if (incidentKey != null) {
      return incidentKey.get();
    }
    return MISSING_INCIDENT;
  }

  @Override
  public boolean isJobIncident(final IncidentRecord record) {
    return record.getJobKey() > 0;
  }

  @Override
  public void forExistingProcessIncident(
      final long elementInstanceKey, final ObjLongConsumer<IncidentRecord> resolver) {
    final long processIncidentKey = getProcessInstanceIncidentKey(elementInstanceKey);

    final boolean hasIncident = processIncidentKey != IncidentState.MISSING_INCIDENT;
    if (hasIncident) {
      final IncidentRecord incidentRecord = getIncidentRecord(processIncidentKey);
      resolver.accept(incidentRecord, processIncidentKey);
    }
  }

  private List<String> getAuthorizedTenantIds(final Map<String, Object> authorizations) {
    return (List<String>) authorizations.get(Authorization.AUTHORIZED_TENANTS);
  }
}
