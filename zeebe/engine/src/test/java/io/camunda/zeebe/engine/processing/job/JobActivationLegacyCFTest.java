/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyJobStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class JobActivationLegacyCFTest {

  private static final String JOB_TYPE = "upgrade-test-type";
  private static final List<String> DEFAULT_TENANT = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  @SuppressWarnings("unused") // injected by ProcessingStateExtension
  private ZeebeDb<ZbColumnFamilies> zeebeDb;

  @SuppressWarnings("unused") // injected by ProcessingStateExtension
  private TransactionContext transactionContext;

  @SuppressWarnings("unused") // injected by ProcessingStateExtension
  private MutableProcessingState processingState;

  private DbJobState jobState;
  private LegacyJobState legacyJobState;

  @BeforeEach
  void setUp() {
    jobState = (DbJobState) processingState.getJobState();
    legacyJobState = new LegacyJobState(zeebeDb, transactionContext);
  }

  /**
   * Runs the 8.2 → 8.3 multi-tenancy migration, which moves all jobs from {@code
   * DEPRECATED_JOB_ACTIVATABLE} to {@code JOB_ACTIVATABLE}. This is how a real pre-8.10 job gets
   * into the legacy column family — every Zeebe cluster that upgraded to 8.3+ ran this migration.
   * After this call the job is in {@code JOB_ACTIVATABLE}, exactly as it would be on an 8.9 broker
   * that is being upgraded to 8.10.
   */
  private void runMultiTenancyMigration() {
    new MultiTenancyJobStateMigration()
        .runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));
  }

  /** Creates a new 8.10-era job. It goes to {@code JOB_ACTIVATABLE_BY_PRIORITY} via M1-4. */
  private void createNewJob(final long key, final int priority) {
    final var record =
        new JobRecord()
            .setType(JOB_TYPE)
            .setRetries(3)
            .setPriority(priority)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(key, record);
  }

  private List<Long> activatedJobKeys() {
    final var keys = new ArrayList<Long>();
    jobState.forEachActivatableJobs(
        BufferUtil.wrapString(JOB_TYPE),
        DEFAULT_TENANT,
        (key, record) -> {
          keys.add(key);
          return true;
        });
    return keys;
  }

  @Test
  void shouldActivateLegacyJobFromJOB_ACTIVATABLE() {
    // given — a pre-8.10 job that was migrated to JOB_ACTIVATABLE during the 8.2→8.3 upgrade
    final var legacyRecord = new JobRecord().setType(JOB_TYPE).setRetries(3);
    legacyJobState.create(1L, legacyRecord);
    runMultiTenancyMigration();

    // when
    final var keys = activatedJobKeys();

    // then — the legacy job is found even though it lives in JOB_ACTIVATABLE, not the priority CF
    assertThat(keys).containsExactly(1L);
  }

  @Test
  void shouldActivateMultipleLegacyJobsInJobKeyAscOrder() {
    // given — two legacy jobs in JOB_ACTIVATABLE, created in ascending key order
    legacyJobState.create(5L, new JobRecord().setType(JOB_TYPE).setRetries(3));
    legacyJobState.create(15L, new JobRecord().setType(JOB_TYPE).setRetries(3));
    runMultiTenancyMigration();

    // when
    final var keys = activatedJobKeys();

    // then — Phase 2 returns legacy jobs in jobKey ASC order (FIFO within the legacy drain)
    assertThat(keys).containsExactly(5L, 15L);
  }

  @Test
  void shouldActivateLegacyJobsBetweenHighAndLowPriorityNewJobs() {
    // given — one legacy pre-8.10 job.
    // Key=25 is intentionally HIGHER than the neutral new job at key=20 so the assertion
    // distinguishes the three-phase ordering from a naive merged key-sort: a key-sort would
    // produce [10, 20, 25, 30], but the correct phase ordering produces [10, 25, 20, 30].
    final var legacyRecord = new JobRecord().setType(JOB_TYPE).setRetries(3);
    legacyJobState.create(25L, legacyRecord);
    runMultiTenancyMigration();

    // and — three new 8.10 jobs
    createNewJob(10L, 5); // high priority → Phase 1 (priority > 0)
    createNewJob(20L, 0); // neutral       → Phase 3 (priority = 0)
    createNewJob(30L, -5); // negative      → Phase 3 (priority < 0)

    // when
    final var keys = activatedJobKeys();

    // then — Phase 1: high-priority new (10); Phase 2: legacy (25); Phase 3: neutral new (20),
    // negative new (30). The legacy job at key=25 precedes the neutral new job at key=20 because
    // Phase 2 runs before Phase 3, not because of key ordering.
    assertThat(keys).containsExactly(10L, 25L, 20L, 30L);
  }
}
