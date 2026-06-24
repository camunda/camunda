/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.AuditLogFixtures.createRandomized;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration test to verify that {@code DefaultExecutionQueue} ensures atomic batch flush
 * behavior. When statements are batched and flushed, they should either all commit or all rollback
 * to maintain data integrity.
 *
 * <p>This test uses the actual RDBMS infrastructure (H2) and does not wrap {@code
 * DefaultExecutionQueue#flush()} in a Spring transaction; the queue manages its JDBC transaction
 * via MyBatis {@code session.commit()} / {@code session.rollback()}.
 */
@Tag("rdbms")
public class RdbmsFlushRollbackIT {

  @RegisterExtension
  static final CamundaRdbmsInvocationContextProviderExtension TEST_APPLICATIONS =
      new CamundaRdbmsInvocationContextProviderExtension("camundaWithH2");

  // Each test uses its own partition ID to avoid row-level conflicts between tests
  private static final int PARTITION_ID_BASIC_ROLLBACK = 10_001;
  private static final int PARTITION_ID_PRE_FLUSH = 10_002;
  private static final int PARTITION_ID_POST_FLUSH = 10_003;
  private static final int PARTITION_ID_HOOK = 10_004;
  private static final int PARTITION_ID_FULL_SCENARIO = 10_005;
  private static final int PARTITION_ID_DUPLICATE_INSERT = 10_006;

  @TestTemplate
  void shouldRollbackAllStatementsWhenSecondStatementFails(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID_BASIC_ROLLBACK);
    final FlowNodeInstanceDbReader flowNodeInstanceReader =
        rdbmsService.getFlowNodeInstanceReader();

    final var duplicateAuditLogKey = "atomicity-duplicate-key";
    final var firstAuditLog = createRandomized(b -> b.auditLogKey(duplicateAuditLogKey));
    final var flowNodeInstance = FlowNodeInstanceFixtures.createRandomized(b -> b);

    rdbmsWriters.getAuditLogWriter().create(firstAuditLog);
    rdbmsWriters.flush();

    rdbmsWriters.getAuditLogWriter().create(firstAuditLog);
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance);

    // when
    assertThatThrownBy(rdbmsWriters::flush).hasMessageContaining(duplicateAuditLogKey);

    // then
    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .isNull();
  }

  /**
   * Verifies that items added to the queue by a pre-flush listener are also rolled back when the
   * flush fails. This is critical because the exporter position UPDATE is added via a pre-flush
   * listener — if it survived a failed flush, the DB position would advance without
   * lastFlushedPosition being updated, causing a permanent "position mismatch" on the next flush.
   */
  @TestTemplate
  void shouldRollbackPreFlushListenerItemsWhenFlushFails(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID_PRE_FLUSH);
    final ExporterPositionService exporterPositionService =
        rdbmsWriters.getExporterPositionService();

    // Seed the exporter position row so UPDATEs can run
    exporterPositionService.createWithoutQueue(
        new ExporterPositionModel(
            PARTITION_ID_PRE_FLUSH, "test-exporter", 0L, LocalDateTime.now(), LocalDateTime.now()));

    // Simulate lastPosition advancing as records are processed, just as the exporter does
    final AtomicLong simulatedLastPosition = new AtomicLong(0L);

    // Register a pre-flush listener that queues an exporter position UPDATE before every flush
    rdbmsWriters
        .getExecutionQueue()
        .registerPreFlushListener(
            () ->
                exporterPositionService.update(
                    new ExporterPositionModel(
                        PARTITION_ID_PRE_FLUSH,
                        "test-exporter",
                        simulatedLastPosition.get(),
                        LocalDateTime.now(),
                        LocalDateTime.now())));

    // Flush 1: succeeds — advances lastPosition to 200, pre-flush listener commits UPDATE to 200
    final var duplicateAuditLogKey = "pre-flush-rollback-key";
    simulatedLastPosition.set(200L);
    rdbmsWriters
        .getAuditLogWriter()
        .create(createRandomized(b -> b.auditLogKey(duplicateAuditLogKey)));
    rdbmsWriters.flush();

    assertThat(exporterPositionService.findOne(PARTITION_ID_PRE_FLUSH).lastExportedPosition())
        .as("Position must be 200 after the first successful flush")
        .isEqualTo(200L);

    // Flush 2 will fail: lastPosition advances to 300, pre-flush listener tries to UPDATE to 300,
    // but the duplicate audit log key causes a PK violation → full rollback
    simulatedLastPosition.set(300L);
    rdbmsWriters
        .getAuditLogWriter()
        .create(createRandomized(b -> b.auditLogKey(duplicateAuditLogKey)));

    // when
    assertThatThrownBy(rdbmsWriters::flush);

    // then: the position UPDATE to 300 queued by the pre-flush listener must be rolled back
    assertThat(exporterPositionService.findOne(PARTITION_ID_PRE_FLUSH).lastExportedPosition())
        .as(
            "Exporter position must NOT have advanced beyond the last successful flush (200) — "
                + "if it advanced to 300, the next flush would throw 'position mismatch'")
        .isEqualTo(200L);
  }

  /**
   * Verifies that the post-flush listener is NOT invoked when the flush fails. This ensures that
   * in-memory state (e.g. lastFlushedPosition) is not advanced when the DB transaction was rolled
   * back.
   */
  @TestTemplate
  void shouldNotCallPostFlushListenerWhenFlushFails(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID_POST_FLUSH);

    final AtomicInteger postFlushCallCount = new AtomicInteger(0);
    rdbmsWriters.getExecutionQueue().registerPostFlushListener(postFlushCallCount::incrementAndGet);

    final var duplicateAuditLogKey = "post-flush-listener-key";
    rdbmsWriters
        .getAuditLogWriter()
        .create(createRandomized(b -> b.auditLogKey(duplicateAuditLogKey)));
    rdbmsWriters.flush();

    assertThat(postFlushCallCount.get()).isEqualTo(1);

    // Queue a duplicate to force failure, plus another item that must also be rolled back
    rdbmsWriters
        .getAuditLogWriter()
        .create(createRandomized(b -> b.auditLogKey(duplicateAuditLogKey)));
    rdbmsWriters
        .getFlowNodeInstanceWriter()
        .create(FlowNodeInstanceFixtures.createRandomized(b -> b));

    // when
    assertThatThrownBy(rdbmsWriters::flush);

    // then: post-flush listener must NOT have been called again
    assertThat(postFlushCallCount.get())
        .as("Post-flush listener must not be called when flush fails")
        .isEqualTo(1);
  }

  /**
   * Verifies that when the in-transaction hook throws (e.g. position mismatch detection), all
   * queued items are rolled back. This mirrors the production scenario where
   * registerLockPositionHook detects divergence and prevents a double-write.
   */
  @TestTemplate
  void shouldRollbackAllItemsWhenInTransactionHookFails(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID_HOOK);
    final FlowNodeInstanceDbReader flowNodeInstanceReader =
        rdbmsService.getFlowNodeInstanceReader();

    final AtomicInteger hookCallCount = new AtomicInteger(0);
    rdbmsWriters
        .getExecutionQueue()
        .registerInTransactionHook(
            session -> {
              if (hookCallCount.incrementAndGet() == 1) {
                throw new IllegalStateException("Simulated position mismatch on first flush");
              }
            });

    final var flowNodeInstance = FlowNodeInstanceFixtures.createRandomized(b -> b);
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance);

    // when: first flush — hook throws
    assertThatThrownBy(rdbmsWriters::flush).hasMessageContaining("Simulated position mismatch");

    // then: the flow node must NOT be in the DB
    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .as("Item must be rolled back when in-transaction hook fails")
        .isNull();

    // and: the queue still holds the item, so the second flush (hook passes) succeeds
    rdbmsWriters.flush();

    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .as("Item must be committed on the successful retry")
        .isNotNull();
  }

  /**
   * Full integration scenario mirroring the observed production failure:
   *
   * <ol>
   *   <li>Exporter position row is seeded and a pre-flush listener simulates position updates.
   *   <li>A successful flush advances the exporter position and calls the post-flush listener.
   *   <li>A second flush fails because the in-transaction hook throws (simulating a transient
   *       failure such as a position mismatch detected after a DB restart).
   *   <li>After the failure the exporter position in the DB must still equal the last successfully
   *       flushed position, and the post-flush listener must not have been called again — i.e. no
   *       "position mismatch" can occur on the next flush attempt.
   *   <li>A retry flush succeeds: all queued items are committed and the position advances.
   * </ol>
   */
  @TestTemplate
  void shouldNotAdvanceExporterPositionAfterFailedFlush(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID_FULL_SCENARIO);
    final ExporterPositionService exporterPositionService =
        rdbmsWriters.getExporterPositionService();
    final FlowNodeInstanceDbReader flowNodeInstanceReader =
        rdbmsService.getFlowNodeInstanceReader();

    // Seed the exporter position
    exporterPositionService.createWithoutQueue(
        new ExporterPositionModel(
            PARTITION_ID_FULL_SCENARIO,
            "test-exporter",
            0L,
            LocalDateTime.now(),
            LocalDateTime.now()));

    // Simulate lastFlushedPosition and lastPosition as the exporter would maintain them
    final AtomicLong lastFlushedPosition = new AtomicLong(0L);
    final AtomicLong lastPosition = new AtomicLong(0L);
    final AtomicInteger postFlushCallCount = new AtomicInteger(0);

    // Pre-flush listener: queue an exporter position UPDATE (mirrors updatePositionInRdbms)
    rdbmsWriters
        .getExecutionQueue()
        .registerPreFlushListener(
            () -> {
              if (lastPosition.get() > lastFlushedPosition.get()) {
                exporterPositionService.update(
                    new ExporterPositionModel(
                        PARTITION_ID_FULL_SCENARIO,
                        "test-exporter",
                        lastPosition.get(),
                        LocalDateTime.now(),
                        LocalDateTime.now()));
              }
            });

    // In-transaction hook: throws on the second flush to simulate a transient failure.
    // Using a hook failure (rather than a PK violation) keeps the queue items valid so the
    // retry flush can succeed — which lets us verify the full recovery path end-to-end.
    final AtomicInteger hookCallCount = new AtomicInteger(0);
    rdbmsWriters
        .getExecutionQueue()
        .registerInTransactionHook(
            session -> {
              if (hookCallCount.incrementAndGet() == 2) {
                throw new IllegalStateException("Simulated transient failure on second flush");
              }
            });

    // Post-flush listener: advance lastFlushedPosition (mirrors the production listener)
    rdbmsWriters
        .getExecutionQueue()
        .registerPostFlushListener(
            () -> {
              lastFlushedPosition.set(lastPosition.get());
              postFlushCallCount.incrementAndGet();
            });

    // --- Flush 1: successful ---
    lastPosition.set(1000L);
    final var flowNodeInstance1 = FlowNodeInstanceFixtures.createRandomized(b -> b);
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance1);
    rdbmsWriters.flush();

    assertThat(lastFlushedPosition.get()).isEqualTo(1000L);
    assertThat(exporterPositionService.findOne(PARTITION_ID_FULL_SCENARIO).lastExportedPosition())
        .isEqualTo(1000L);
    assertThat(postFlushCallCount.get()).isEqualTo(1);

    // --- Flush 2: hook throws → full rollback ---
    lastPosition.set(2000L);
    final var flowNodeInstance2 = FlowNodeInstanceFixtures.createRandomized(b -> b);
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance2);

    assertThatThrownBy(rdbmsWriters::flush)
        .hasMessageContaining("Simulated transient failure on second flush");

    // then: critical invariants after failure

    // 1. lastFlushedPosition must NOT have advanced
    assertThat(lastFlushedPosition.get())
        .as("lastFlushedPosition must not advance after a failed flush")
        .isEqualTo(1000L);

    // 2. DB exporter position must match lastFlushedPosition (no partial commit)
    assertThat(exporterPositionService.findOne(PARTITION_ID_FULL_SCENARIO).lastExportedPosition())
        .as(
            "DB exporter position must equal lastFlushedPosition after rollback — "
                + "any divergence here causes permanent 'position mismatch' on the next retry")
        .isEqualTo(lastFlushedPosition.get());

    // 3. Post-flush listener must not have been called again
    assertThat(postFlushCallCount.get())
        .as("Post-flush listener must not fire on a failed flush")
        .isEqualTo(1);

    // 4. The flow node from the failed flush must not be in the DB
    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance2.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .as("Items from the failed flush must be fully rolled back")
        .isNull();

    // 5. The retry flush must succeed without a position mismatch exception.
    //    The queue still holds flowNodeInstance2 and the position UPDATE to 2000.
    //    If invariant 2 above is violated the hook would throw "position mismatch" here.
    rdbmsWriters.flush();

    assertThat(lastFlushedPosition.get())
        .as("lastFlushedPosition must advance after the successful retry")
        .isEqualTo(2000L);
    assertThat(exporterPositionService.findOne(PARTITION_ID_FULL_SCENARIO).lastExportedPosition())
        .as("DB position must match lastFlushedPosition after the successful retry")
        .isEqualTo(2000L);
    assertThat(postFlushCallCount.get())
        .as("Post-flush listener must fire exactly once for the retry flush")
        .isEqualTo(2);
    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance2.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .as("Items must be committed on the successful retry")
        .isNotNull();
  }

  /**
   * Verifies that creating the same entity twice within a single flush (as happens when the
   * exporter re-processes and re-enqueues a record after a failed flush) does not produce a
   * duplicate row in the batch INSERT. The {@code BatchInsertMerger} must absorb the second insert
   * so the flush succeeds with a single row instead of failing with a primary-key violation.
   */
  @TestTemplate
  void shouldDropDuplicateInsertWithinSameFlush(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID_DUPLICATE_INSERT);
    final FlowNodeInstanceDbReader flowNodeInstanceReader =
        rdbmsService.getFlowNodeInstanceReader();

    final var flowNodeInstance = FlowNodeInstanceFixtures.createRandomized(b -> b);

    // the same flow node instance is queued twice into one flush (mirrors the re-enqueue on retry)
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance);
    rdbmsWriters.getFlowNodeInstanceWriter().create(flowNodeInstance);

    // when: the flush must succeed (the duplicate is absorbed), not throw a duplicate-key error
    rdbmsWriters.flush();

    // then: the row exists exactly once
    assertThat(
            flowNodeInstanceReader.getByKey(
                flowNodeInstance.flowNodeInstanceKey(), resourceAccessChecksFromTenantIds()))
        .as("The de-duplicated flow node must be committed exactly once")
        .isNotNull();
  }
}
