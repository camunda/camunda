/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the real {@link EventAppliers#applyState} version-dispatch path for #56962 — the
 * mechanism that resolves a persisted record's own {@code recordVersion} to an applier at replay
 * time.
 *
 * <p>recordVersion=2 reproduces replaying a CREATED event originally processed and persisted by a
 * pre-#53724 broker, whose latest registered CREATED version was 2 (confirmed via {@code git show
 * 840b8d064a68:.../EventAppliers.java}, the last commit before #53724).
 */
@ExtendWith(ProcessingStateExtension.class)
final class JobActivatableReplayVersionDispatchTest {

  private MutableProcessingState processingState;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;

  private MutableJobState jobState;
  private MutableIncidentState incidentState;

  private ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>, DbNil>
      legacyActivatableColumnFamily;
  private ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>>,
          DbNil>
      priorityActivatableColumnFamily;

  private EventAppliers eventAppliers;

  @BeforeEach
  void setUp() {
    jobState = processingState.getJobState();
    incidentState = processingState.getIncidentState();
    eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);

    final DbLong jobKey = new DbLong();
    final DbForeignKey<DbLong> fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    final DbString jobTypeKey = new DbString();
    final DbString tenantIdKey = new DbString();

    final var typeJobKey = new DbCompositeKey<>(jobTypeKey, fkJob);
    final var tenantAwareTypeJobKey =
        new DbTenantAwareKey<>(tenantIdKey, typeJobKey, PlacementType.SUFFIX);
    legacyActivatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE,
            transactionContext,
            tenantAwareTypeJobKey,
            DbNil.INSTANCE);

    final DbInt invertedPriorityKey = new DbInt();
    final var invertedPriorityJobKey = new DbCompositeKey<>(invertedPriorityKey, fkJob);
    final var typePriorityJobKey = new DbCompositeKey<>(jobTypeKey, invertedPriorityJobKey);
    final var tenantAwarePriorityKey =
        new DbTenantAwareKey<>(tenantIdKey, typePriorityJobKey, PlacementType.SUFFIX);
    priorityActivatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY,
            transactionContext,
            tenantAwarePriorityKey,
            DbNil.INSTANCE);
  }

  @Test
  void shouldDispatchPreExistingVersionToLegacyColumnFamilyOnReplay() {
    // given a CREATED record as it would have been stamped by a pre-#53724 broker, whose latest
    // registered CREATED version was 2
    final long key = 1L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(-1L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.CREATED, jobRecord, 2);

    // then it must land in the legacy JOB_ACTIVATABLE column family, matching what the original
    // pre-#53724 broker actually wrote, not the new priority column family
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=2 (JobCreatedV2Applier) must dispatch through the legacy insert path")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=2 must not write JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchNewVersionToPriorityColumnFamilyOnReplay() {
    // given a CREATED record stamped with this fix's new version
    final long key = 2L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(-1L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.CREATED, jobRecord, 3);

    // then it must land in the new priority column family, and never the legacy one
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=3 (JobCreatedV3Applier) must dispatch through the priority insert path")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=3 must not write the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchPreExistingFailedVersionToLegacyColumnFamilyOnReplay() {
    // given a FAILED record with retries remaining and no backoff, as it would have been stamped
    // by a pre-#53724 broker, whose latest registered FAILED version was 2
    final long key = 3L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setRetryBackoff(0L)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.FAILED, jobRecord, 2);

    // then it must land in the legacy JOB_ACTIVATABLE column family
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=2 (JobFailedV2Applier) must dispatch through the legacy insert path")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=2 must not write JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchNewFailedVersionToPriorityColumnFamilyOnReplay() {
    // given a FAILED record stamped with this fix's new version
    final long key = 4L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setRetryBackoff(0L)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.FAILED, jobRecord, 3);

    // then it must land in the new priority column family, and never the legacy one
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=3 (JobFailedV3Applier) must dispatch through the priority insert path")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=3 must not write the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchPreExistingTimedOutVersionToLegacyColumnFamilyOnReplay() {
    // given a TIMED_OUT record as it would have been stamped by a pre-#53724 broker, whose latest
    // registered TIMED_OUT version was 2
    final long key = 5L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.TIMED_OUT, jobRecord, 2);

    // then it must land in the legacy JOB_ACTIVATABLE column family
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=2 (JobTimedOutV2Applier) must dispatch through the legacy insert path")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=2 must not write JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchNewTimedOutVersionToPriorityColumnFamilyOnReplay() {
    // given a TIMED_OUT record stamped with this fix's new version
    final long key = 6L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.TIMED_OUT, jobRecord, 3);

    // then it must land in the new priority column family, and never the legacy one
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=3 (JobTimedOutV3Applier) must dispatch through the priority insert path")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=3 must not write the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchPreExistingRecurredVersionToLegacyColumnFamilyOnReplay() {
    // given a RECURRED_AFTER_BACKOFF record as it would have been stamped by a pre-#53724 broker,
    // whose latest registered RECURRED_AFTER_BACKOFF version was 1
    final long key = 7L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setRecurringTime(500L)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.RECURRED_AFTER_BACKOFF, jobRecord, 1);

    // then it must land in the legacy JOB_ACTIVATABLE column family
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=1 (JobRecurredApplier) must dispatch through the legacy insert path")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=1 must not write JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchNewRecurredVersionToPriorityColumnFamilyOnReplay() {
    // given a RECURRED_AFTER_BACKOFF record stamped with this fix's new version
    final long key = 8L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setRecurringTime(500L)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.RECURRED_AFTER_BACKOFF, jobRecord, 2);

    // then it must land in the new priority column family, and never the legacy one
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=2 (JobRecurredV2Applier) must dispatch through the priority insert path")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=2 must not write the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchPreExistingYieldedVersionToLegacyColumnFamilyOnReplay() {
    // given a YIELDED record as it would have been stamped by a pre-#53724 broker, whose latest
    // registered YIELDED version was 1
    final long key = 9L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.YIELDED, jobRecord, 1);

    // then it must land in the legacy JOB_ACTIVATABLE column family
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=1 (JobYieldedApplier) must dispatch through the legacy insert path")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=1 must not write JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchNewYieldedVersionToPriorityColumnFamilyOnReplay() {
    // given a YIELDED record stamped with this fix's new version
    final long key = 10L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(key, JobIntent.YIELDED, jobRecord, 2);

    // then it must land in the new priority column family, and never the legacy one
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=2 (JobYieldedV2Applier) must dispatch through the priority insert path")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=2 must not write the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchPreExistingResolvedVersionToLegacyColumnFamilyOnReplay() {
    // given a FAILED job with an open incident, and a RESOLVED record as it would have been
    // stamped by a pre-#53724 broker, whose latest registered RESOLVED version was 3
    final long jobKey = 11L;
    final long incidentKey = 100L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType("test")
            .setElementId("elementId")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(jobKey, jobRecord);
    jobState.updateJobState(jobKey, State.FAILED);

    final IncidentRecord incidentRecord =
        new IncidentRecord()
            .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .setJobKey(jobKey)
            .setElementId(BufferUtil.wrapString("elementId"));
    incidentState.createIncident(incidentKey, incidentRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(incidentKey, IncidentIntent.RESOLVED, incidentRecord, 3);

    // then it must land in the legacy JOB_ACTIVATABLE column family
    assertThat(legacyActivatableColumnFamily.count())
        .as(
            "recordVersion=3 (IncidentResolvedV3Applier) must dispatch through the legacy insert path")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("recordVersion=3 must not write JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldDispatchNewResolvedVersionToPriorityColumnFamilyOnReplay() {
    // given a FAILED job with an open incident, and a RESOLVED record stamped with this fix's new
    // version
    final long jobKey = 12L;
    final long incidentKey = 101L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setElementId("elementId")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(jobKey, jobRecord);
    jobState.updateJobState(jobKey, State.FAILED);

    final IncidentRecord incidentRecord =
        new IncidentRecord()
            .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .setJobKey(jobKey)
            .setElementId(BufferUtil.wrapString("elementId"));
    incidentState.createIncident(incidentKey, incidentRecord);

    // when replayed via the real dispatch path using the record's own stored version
    eventAppliers.applyState(incidentKey, IncidentIntent.RESOLVED, incidentRecord, 4);

    // then it must land in the new priority column family, and never the legacy one
    assertThat(priorityActivatableColumnFamily.count())
        .as(
            "recordVersion=4 (IncidentResolvedV4Applier) must dispatch through the priority insert path")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("recordVersion=4 must not write the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }
}
