/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

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
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproduces #56962: replaying a pre-8.10 {@code CREATED} job event must land the activatable job
 * in the legacy {@code JOB_ACTIVATABLE} column family, exactly as the original processing did.
 *
 * <p>Since #53724, {@code DbJobState.create} routes every activatable job into the new {@code
 * JOB_ACTIVATABLE_BY_PRIORITY} column family, so replay diverges from original processing for
 * pre-8.10 events. This test fails before the fix and passes after it.
 */
final class JobActivatableReplayDivergenceTest {

  @TempDir Path tempDir;

  private ZeebeDb<ZbColumnFamilies> db;
  private TransactionContext transactionContext;
  private DbJobState jobState;

  private ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>, DbNil>
      legacyActivatableColumnFamily;
  private ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>>,
          DbNil>
      priorityActivatableColumnFamily;

  @BeforeEach
  void setUp() throws IOException {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(tempDir.toFile());
    transactionContext = db.createContext();
    jobState = new DbJobState(db, transactionContext);

    final DbLong jobKey = new DbLong();
    final DbForeignKey<DbLong> fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    final DbString jobTypeKey = new DbString();
    final DbString tenantIdKey = new DbString();

    final var typeJobKey = new DbCompositeKey<>(jobTypeKey, fkJob);
    final var tenantAwareTypeJobKey =
        new DbTenantAwareKey<>(tenantIdKey, typeJobKey, PlacementType.SUFFIX);
    legacyActivatableColumnFamily =
        db.createColumnFamily(
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
        db.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY,
            transactionContext,
            tenantAwarePriorityKey,
            DbNil.INSTANCE);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldWriteActivatableJobFromLegacyCreatedEventToLegacyColumnFamily() {
    // given a pre-8.10 CREATED event replayed through the old (unversioned) create path
    final long key = 1L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(2)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when
    jobState.create(key, jobRecord);

    // then the activatable entry must be written to the legacy JOB_ACTIVATABLE column family,
    // matching how the pre-8.10 broker originally mutated state.
    assertThat(legacyActivatableColumnFamily.count())
        .as("pre-8.10 CREATED event must write to the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(1L);
    assertThat(priorityActivatableColumnFamily.count())
        .as("pre-8.10 CREATED event must not write to JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
  }

  @Test
  void shouldWriteActivatableJobFromNewVersionApplierToPriorityColumnFamilyOnly() {
    // given a post-8.10 CREATED event replayed through the new (versioned) create path, i.e. the
    // same two calls that JobCreatedV3Applier makes
    final long key = 2L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(2)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when
    jobState.insertJobRecordActivatable(key, jobRecord);
    jobState.makeJobActivatableByPriority(
        jobRecord.getTypeBuffer(), key, jobRecord.getTenantId(), jobRecord.getPriority());

    // then the activatable entry must be written to JOB_ACTIVATABLE_BY_PRIORITY, and never to the
    // legacy JOB_ACTIVATABLE column family.
    assertThat(priorityActivatableColumnFamily.count())
        .as("post-8.10 CREATED event must write to JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(1L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("post-8.10 CREATED event must not write to the legacy JOB_ACTIVATABLE column family")
        .isEqualTo(0L);
  }

  @Test
  void shouldRemoveJobFromBothActivatableColumnFamiliesIdempotently() {
    // given a job made activatable through the new priority-based path
    final long key = 3L;
    final JobRecord jobRecord =
        new JobRecord()
            .setRetries(2)
            .setDeadline(256L)
            .setType("test")
            .setPriority(5)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);
    jobState.makeJobActivatableByPriority(
        jobRecord.getTypeBuffer(), key, jobRecord.getTenantId(), jobRecord.getPriority());
    assertThat(priorityActivatableColumnFamily.count()).isEqualTo(1L);

    // when removed the first time
    jobState.makeJobNotActivatable(jobRecord);

    // then both column families are empty
    assertThat(priorityActivatableColumnFamily.count())
        .as("removal must delete the entry from JOB_ACTIVATABLE_BY_PRIORITY")
        .isEqualTo(0L);
    assertThat(legacyActivatableColumnFamily.count())
        .as("removal must be a no-op on the legacy JOB_ACTIVATABLE column family when absent")
        .isEqualTo(0L);

    // when removed a second time (idempotency, e.g. re-applied during replay)
    jobState.makeJobNotActivatable(jobRecord);

    // then no error occurs and both column families remain empty
    assertThat(priorityActivatableColumnFamily.count()).isEqualTo(0L);
    assertThat(legacyActivatableColumnFamily.count()).isEqualTo(0L);
  }
}
