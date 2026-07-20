/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbJobStateLegacyDrainTest {

  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableProcessingState processingState;

  private MutableJobState jobState;

  private DbLong seedJobKey;
  private ColumnFamily<DbLong, JobRecordValue> seedJobsColumnFamily;
  private DbString seedJobTypeKey;
  private DbString seedTenantIdKey;
  private DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>
      seedTenantAwareTypeJobKey;
  private ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>, DbNil>
      legacyColumnFamily;

  @BeforeEach
  void setUp() {
    jobState = processingState.getJobState();

    seedJobKey = new DbLong();
    final var seedFkJob = new DbForeignKey<>(seedJobKey, ZbColumnFamilies.JOBS);
    seedJobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, seedJobKey, new JobRecordValue());

    seedJobTypeKey = new DbString();
    seedTenantIdKey = new DbString();
    final var seedTypeJobKey = new DbCompositeKey<>(seedJobTypeKey, seedFkJob);
    seedTenantAwareTypeJobKey =
        new DbTenantAwareKey<>(seedTenantIdKey, seedTypeJobKey, PlacementType.SUFFIX);
    legacyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE,
            transactionContext,
            seedTenantAwareTypeJobKey,
            DbNil.INSTANCE);
  }

  @Test
  void shouldStillFindLegacyJobWhenAnotherTypeKeepsCfNonEmpty() {
    // given
    final DirectBuffer typeA = wrapString("type-a");
    final DirectBuffer typeB = wrapString("type-b");
    seedLegacyJob(1L, typeB, TENANT);

    // when: type A's own legacy scan is empty, but the CF is not globally empty (type B remains)
    assertThat(activatableKeys(typeA)).isEmpty();

    // then: the flag must not have been set — a legacy job seeded for type A afterwards is still
    // found on the next activation
    seedLegacyJob(2L, typeA, TENANT);
    assertThat(activatableKeys(typeA)).containsExactly(2L);
  }

  @Test
  void shouldSkipPhase2OnceLegacyCfIsGloballyEmpty() {
    // given: legacy CF is empty from the start
    final DirectBuffer type = wrapString("type-a");
    assertThat(activatableKeys(type)).isEmpty(); // flips the flag

    // when: a legacy job is seeded directly afterwards (simulating a leftover legacy entry)
    seedLegacyJob(1L, type, TENANT);

    // then: it is not returned, because Phase 2 is now skipped
    assertThat(activatableKeys(type)).isEmpty();
  }

  @Test
  void shouldReturnEmptyOnFreshClusterWithNoLegacyJobs() {
    // given
    final DirectBuffer type = wrapString("type-a");

    // when
    final List<Long> keys = activatableKeys(type);

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  void shouldPreserveActivationOrderAcrossPhases() {
    // given
    final DirectBuffer type = wrapString("type-a");
    // legacy (pre-8.10) jobs: implicit priority = 0, ordered by job key ascending
    seedLegacyJob(1L, type, TENANT);
    seedLegacyJob(2L, type, TENANT);
    // new jobs with priority > 0
    createActivatableJob(11L, type, 10);
    createActivatableJob(10L, type, 5);
    // new jobs with priority <= 0
    createActivatableJob(20L, type, 0);
    createActivatableJob(21L, type, -5);

    // when
    final List<Long> keys = activatableKeys(type);

    // then: priority > 0 (desc) -> legacy (jobKey asc) -> priority <= 0 (desc)
    assertThat(keys).containsExactly(11L, 10L, 1L, 2L, 20L, 21L);
  }

  @Test
  void shouldNotEnterPhase2WhenPhase1FillsTheBatch() {
    // given: a priority>0 job (Phase 1) and a legacy job (Phase 2) for the same type
    final DirectBuffer type = wrapString("type-a");
    createActivatableJob(10L, type, 5);
    seedLegacyJob(1L, type, TENANT);

    // when: the callback reports the batch full on the very first (Phase 1) job,
    // short-circuiting Phase 2 entirely
    assertThat(activatableKeys(type, 1)).containsExactly(10L);

    // then: the flag was not set by the short-circuited call, so Phase 2 still runs on the next,
    // unrestricted call and finds the legacy job
    assertThat(activatableKeys(type)).containsExactly(10L, 1L);
  }

  @Test
  void shouldNotSetFlagWhenLegacyJobExistsForSameTypeButDifferentTenant() {
    // given: a legacy job of the queried type but a tenant outside the queried tenant list
    final DirectBuffer type = wrapString("type-a");
    final String otherTenant = "other-tenant";
    seedLegacyJob(1L, type, otherTenant);

    // when: querying only TENANT finds nothing, because the entry belongs to another tenant
    assertThat(activatableKeys(type)).isEmpty();

    // then: the flag must not have been set — a legacy job seeded for the queried tenant
    // afterwards is still found
    seedLegacyJob(2L, type, TENANT);
    assertThat(activatableKeys(type)).containsExactly(2L);
  }

  @Test
  void shouldFindAndDrainLegacyJobInsertedByReplayOfPreVersionedCreatedEvent() {
    // given: the real applier version-dispatch used at replay, not a direct CF seed
    final var eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
    final DirectBuffer type = wrapString("type-a");
    final JobRecord createdRecord =
        new JobRecord().setType(type).setTenantId(TENANT).setRetries(3).setDeadline(-1L);

    // when: a CREATED event as persisted by a pre-#53724 broker (recordVersion=2) is replayed
    // through the real dispatch path, which must land the job in the legacy JOB_ACTIVATABLE CF
    eventAppliers.applyState(1L, JobIntent.CREATED, createdRecord, 2);

    // then: forEachActivatableJobs finds it via Phase 2 — the flag must not be set while a
    // replay-inserted legacy entry still exists
    assertThat(activatableKeys(type)).containsExactly(1L);

    // when: the job is activated, which removes it from the legacy CF the same way
    // makeJobNotActivatable does for any other deactivation
    final JobRecord activateRecord =
        new JobRecord().setType(type).setTenantId(TENANT).setRetries(3).setDeadline(1_000L);
    jobState.activate(1L, activateRecord);

    // then: the legacy CF is now globally empty, so the flag is set on this scan and a legacy
    // entry seeded afterwards is never found again
    assertThat(activatableKeys(type)).isEmpty();
    seedLegacyJob(2L, type, TENANT);
    assertThat(activatableKeys(type)).isEmpty();
  }

  @Test
  void shouldResetFlagOnRestart() {
    // given: drain the flag on the original state instance
    final DirectBuffer type = wrapString("type-a");
    assertThat(activatableKeys(type)).isEmpty();
    seedLegacyJob(1L, type, TENANT);
    assertThat(activatableKeys(type)).isEmpty(); // Phase 2 skipped: flag was set

    // when: a fresh DbJobState instance is constructed over the same database (restart)
    final var restarted = new DbJobState(zeebeDb, transactionContext);

    // then: the new instance starts undrained and finds the leftover legacy job
    assertThat(activatableKeys(restarted, type)).containsExactly(1L);
  }

  // Mirrors JobCreatedV3Applier: a genuinely new (post-8.10) job goes through the
  // priority-aware insertion path, not the deprecated, legacy-only create().
  private void createActivatableJob(final long key, final DirectBuffer type, final int priority) {
    final JobRecord record =
        new JobRecord().setType(type).setTenantId(TENANT).setPriority(priority).setRetries(1);
    jobState.insertJobRecordActivatable(key, record);
    jobState.makeJobActivatableByPriority(type, key, TENANT, priority);
  }

  private void seedLegacyJob(final long key, final DirectBuffer type, final String tenantId) {
    final JobRecord record = new JobRecord().setType(type).setTenantId(tenantId).setRetries(1);
    final var value = new JobRecordValue();
    value.setRecordWithoutVariables(record);
    seedJobKey.wrapLong(key);
    seedJobsColumnFamily.insert(seedJobKey, value);

    seedJobTypeKey.wrapBuffer(type);
    seedTenantIdKey.wrapString(tenantId);
    legacyColumnFamily.insert(seedTenantAwareTypeJobKey, DbNil.INSTANCE);
  }

  // Unlimited batch: all three phases run. Used by most tests.
  private List<Long> activatableKeys(final DirectBuffer type) {
    return activatableKeys(jobState, type, Integer.MAX_VALUE);
  }

  // Batch-limited: simulates a worker whose batch fills after maxResults jobs.
  // Returning false from the callback short-circuits Phase 2 (legacy CF) and Phase 3,
  // because each phase gate checks whether the previous phase left the batch not full.
  private List<Long> activatableKeys(final DirectBuffer type, final int maxResults) {
    return activatableKeys(jobState, type, maxResults);
  }

  // Targets an explicit state instance — needed when testing a simulated restart,
  // where the in-memory legacyCfDrained flag is reset to its initial false value.
  private List<Long> activatableKeys(final MutableJobState state, final DirectBuffer type) {
    return activatableKeys(state, type, Integer.MAX_VALUE);
  }

  private List<Long> activatableKeys(
      final MutableJobState state, final DirectBuffer type, final int maxResults) {
    final List<Long> keys = new ArrayList<>();
    state.forEachActivatableJobs(
        type,
        List.of(TENANT),
        (k, e) -> {
          keys.add(k);
          return keys.size() < maxResults;
        });
    return keys;
  }
}
