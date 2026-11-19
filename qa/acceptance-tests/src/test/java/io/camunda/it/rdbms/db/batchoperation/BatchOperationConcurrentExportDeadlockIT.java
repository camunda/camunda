/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.batchoperation;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.NOW;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemCompletionDto;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Regression suite for camunda/camunda#58634.
 *
 * <p>Simulates several partition exporters, each owning its own {@code ExecutionQueue}, writing to
 * the shared {@code BATCH_OPERATION} (and item) rows in a partition-specific order, the way the
 * real exporters do when lifecycle commands are distributed to all partitions. Every scenario
 * asserts that no flush is chosen as a deadlock victim and that all queues drain.
 */
@Tag("rdbms")
public class BatchOperationConcurrentExportDeadlockIT {

  @RegisterExtension
  static final CamundaRdbmsInvocationContextProviderExtension TEST_APPLICATIONS =
      // H2 is excluded: its MERGE is not atomic, so concurrent first creates of the same key fail
      // with a duplicate key error, which is independent of the lock order under test
      new CamundaRdbmsInvocationContextProviderExtension(
          "camundaWithPostgresSQL",
          "camundaWithMariaDB",
          "camundaWithMySQL",
          "camundaWithOracleDB",
          "camundaWithMssqlDB");

  private static final int PARTITIONS = 8;
  private static final int BATCHES = 10;
  // every partition updates the same batch operations, so their flushes commit one after the other
  private static final Duration DEADLINE = Duration.ofSeconds(60);
  private static final Duration FLUSH_INTERVAL = Duration.ofMillis(500);

  /** Only suspend/resume on every partition, random key order per partition. */
  @TestTemplate
  void shouldNotDeadlockWhenAllPartitionsSuspendAndResume(final CamundaRdbmsTestApplication app) {
    // given / when
    final var result = run(app, Workload.SUSPEND_RESUME, 3, false, false);

    // then
    assertClean(result);
  }

  /** Every statement type touching BATCH_OPERATION, random mix and key order per partition. */
  @TestTemplate
  void shouldNotDeadlockWhenAllPartitionsWriteMixedLifecycleStatements(
      final CamundaRdbmsTestApplication app) {
    // given / when
    final var result = run(app, Workload.MIXED, 3, false, false);

    // then
    assertClean(result);
  }

  /** Cancel racing item inserts on every partition. */
  @TestTemplate
  void shouldNotDeadlockWhenCancelRacesItemWrites(final CamundaRdbmsTestApplication app) {
    // given / when
    final var result = run(app, Workload.CANCEL_VS_ITEMS, 3, false, false);

    // then
    assertClean(result);
  }

  /** Same as mixed, but the exporter keeps ingesting records while a flush keeps failing. */
  @TestTemplate
  void shouldNotDeadlockWhenTheQueueGrowsDuringRetries(final CamundaRdbmsTestApplication app) {
    // given / when
    final var result = run(app, Workload.MIXED, 2, true, false);

    // then
    assertClean(result);
  }

  /** Mixed exporter flushes plus the direct (non-queued) writers: bulk completion and cleanup. */
  @TestTemplate
  void shouldNotDeadlockWhenDirectWritersRunAlongsideTheExporters(
      final CamundaRdbmsTestApplication app) {
    // given / when
    final var result = run(app, Workload.MIXED, 3, false, true);

    // then
    assertClean(result);
  }

  /**
   * Every partition exports the creation of the same new batch operation in its own flush. The
   * insert already takes the row lock, so locking the key up front as well would take gap locks on
   * MySQL and MariaDB and deadlock the concurrent inserts.
   */
  @TestTemplate
  void shouldNotDeadlockWhenAllPartitionsCreateTheSameBatchOperation(
      final CamundaRdbmsTestApplication app) {
    // given
    final var total = new Result();

    // when
    for (int round = 0; round < 20; round++) {
      total.add(run(app, Workload.SINGLE_CREATE, 1, false, false));
    }

    // then
    assertClean(total);
  }

  /**
   * Every partition re-exports the creation of batch operations that already exist, interleaved
   * with updates of other batch operations, in a partition-specific key order. The created keys are
   * not locked up front, so this pins down that their later locking cannot form a cycle.
   */
  @TestTemplate
  void shouldNotDeadlockWhenPartitionsRecreateExistingBatchOperationsAmongUpdates(
      final CamundaRdbmsTestApplication app) {
    // given
    final var total = new Result();

    // when
    for (int round = 0; round < 6; round++) {
      total.add(run(app, Workload.CREATE_EXISTING, 2, false, false));
    }

    // then
    assertClean(total);
  }

  /** Many short rounds with fresh random orders, to catch low-probability interleavings. */
  @TestTemplate
  void shouldNotDeadlockAcrossManyRoundsOfMixedWorkload(final CamundaRdbmsTestApplication app) {
    // given
    final var total = new Result();

    // when
    for (int round = 0; round < 6; round++) {
      total.add(run(app, Workload.MIXED, 1, false, false));
    }

    // then
    assertClean(total);
  }

  private static void assertClean(final Result result) {
    assertThat(result.deadlocks.get()).as("deadlock victims").isZero();
    assertThat(result.otherFailures.get()).as("non-deadlock flush failures").isZero();
    assertThat(result.unfinishedPartitions.get()).as("partitions that never drained").isZero();
    assertThat(result.directWriterFailures.get()).as("direct writer failures").isZero();
  }

  private static boolean isDeadlock(final Throwable t) {
    for (var cause = t; cause != null; cause = cause.getCause()) {
      for (SQLException se = cause instanceof SQLException s ? s : null;
          se != null;
          se = se.getNextException()) {
        // 40P01 postgres, 40001 mssql (1205) and mysql/mariadb (1213), ORA-00060 oracle
        if ("40P01".equals(se.getSQLState())
            || "40001".equals(se.getSQLState())
            || se.getErrorCode() == 60) {
          return true;
        }
      }
    }
    return false;
  }

  private static Result run(
      final CamundaRdbmsTestApplication app,
      final Workload workload,
      final int cycles,
      final boolean ingestWhileFailing,
      final boolean withDirectWriters) {
    final RdbmsService rdbmsService = app.getRdbmsService();
    final var setup = rdbmsService.createWriter(0);
    final List<String> keys = new ArrayList<>();
    final List<BatchOperationDbModel> models = new ArrayList<>();
    final boolean preCreate = workload != Workload.SINGLE_CREATE;
    final int batches = workload == Workload.SINGLE_CREATE ? 1 : BATCHES;
    for (int i = 0; i < batches; i++) {
      final var model = BatchOperationFixtures.createRandomized(b -> b);
      models.add(model);
      keys.add(model.batchOperationKey());
      if (preCreate) {
        setup.getBatchOperationWriter().create(model);
      }
    }
    if (preCreate) {
      setup.flush();
    }

    final var result = new Result();
    final var start = new CountDownLatch(1);
    final var done = new CountDownLatch(PARTITIONS);
    final var pool = Executors.newFixedThreadPool(PARTITIONS);
    final var directWriter = Executors.newSingleThreadScheduledExecutor();
    try {
      if (withDirectWriters) {
        scheduleDirectWriter(directWriter, rdbmsService, keys, result);
      }
      for (int p = 1; p <= PARTITIONS; p++) {
        final int partition = p;
        pool.submit(
            () -> {
              try {
                final var writer = rdbmsService.createWriter(partition);
                final var random = new Random(System.nanoTime() + partition);
                final List<BatchOperationItemDbModel> ownItems = new ArrayList<>();
                for (int c = 0; c < cycles; c++) {
                  enqueue(writer, workload, keys, models, random, ownItems);
                }
                start.await();
                flushUntilDrained(
                    writer,
                    result,
                    ingestWhileFailing
                        ? () -> enqueue(writer, workload, keys, models, random, ownItems)
                        : null);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (final Throwable e) {
                result.otherFailures.incrementAndGet();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      try {
        if (!done.await(DEADLINE.plusSeconds(15).toMillis(), TimeUnit.MILLISECONDS)) {
          result.unfinishedPartitions.addAndGet((int) done.getCount());
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } finally {
      directWriter.shutdownNow();
      pool.shutdownNow();
    }
    return result;
  }

  /** Mimics the exporter: the interval flush swallows the failure and tries again. */
  private static void flushUntilDrained(
      final RdbmsWriters writer, final Result result, final Runnable ingestWhileFailing) {
    try {
      await()
          .atMost(DEADLINE)
          .pollDelay(Duration.ZERO)
          .pollInterval(FLUSH_INTERVAL)
          .until(
              () -> {
                try {
                  writer.flush();
                  return true;
                } catch (final Exception e) {
                  (isDeadlock(e) ? result.deadlocks : result.otherFailures).incrementAndGet();
                  if (ingestWhileFailing != null) {
                    ingestWhileFailing.run();
                  }
                  return false;
                }
              });
    } catch (final ConditionTimeoutException e) {
      result.unfinishedPartitions.incrementAndGet();
    }
  }

  /** The same direct (non-queue) calls the history deletion service makes. */
  private static void scheduleDirectWriter(
      final ScheduledExecutorService executor,
      final RdbmsService rdbmsService,
      final List<String> keys,
      final Result result) {
    final RdbmsWriters writer = rdbmsService.createWriter(99);
    final var random = new Random();
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            final var batch = keys.get(random.nextInt(keys.size()));
            writer
                .getBatchOperationWriter()
                .completeBatchOperationItems(
                    List.of(new BatchOperationItemCompletionDto(batch, nextKey())), NOW);
            writer.getBatchOperationWriter().cleanupHistory(OffsetDateTime.now(), 100);
          } catch (final Exception e) {
            result.directWriterFailures.incrementAndGet();
          }
        },
        0,
        20,
        TimeUnit.MILLISECONDS);
  }

  /** Queue one cycle of records the way a partition exporter would, in a random key order. */
  private static void enqueue(
      final RdbmsWriters w,
      final Workload workload,
      final List<String> keys,
      final List<BatchOperationDbModel> models,
      final Random random,
      final List<BatchOperationItemDbModel> ownItems) {
    final var writer = w.getBatchOperationWriter();
    final var order = new ArrayList<>(keys);
    Collections.shuffle(order, random);
    for (final var key : order) {
      switch (workload) {
        case SINGLE_CREATE -> writer.create(models.get(0));
        case CREATE_EXISTING -> {
          if (random.nextBoolean()) {
            writer.create(models.get(keys.indexOf(key)));
          }
          writer.suspend(key);
          writer.resume(key);
        }
        case SUSPEND_RESUME -> {
          writer.suspend(key);
          writer.resume(key);
        }
        case MIXED -> {
          if (random.nextInt(3) == 0) {
            writer.activate(key, OffsetDateTime.now());
          }
          if (random.nextInt(2) == 0) {
            final var items = newItems(key, 3);
            ownItems.addAll(items);
            writer.updateBatchAndInsertItems(key, items);
          }
          if (!ownItems.isEmpty() && random.nextInt(2) == 0) {
            final var source = ownItems.get(random.nextInt(ownItems.size()));
            final var state =
                random.nextBoolean()
                    ? BatchOperationItemState.COMPLETED
                    : BatchOperationItemState.FAILED;
            writer.updateItem(
                new BatchOperationItemDbModel(
                    source.batchOperationKey(),
                    source.itemKey(),
                    source.processInstanceKey(),
                    source.rootProcessInstanceKey(),
                    state,
                    NOW,
                    state == BatchOperationItemState.FAILED ? "boom" : null));
          }
          switch (random.nextInt(5)) {
            case 0 -> writer.suspend(key);
            case 1 -> writer.resume(key);
            case 2 -> writer.finish(key, OffsetDateTime.now());
            case 3 -> writer.cancel(key, OffsetDateTime.now());
            default -> {}
          }
        }
        case CANCEL_VS_ITEMS -> {
          final var items = newItems(key, 3);
          ownItems.addAll(items);
          writer.updateBatchAndInsertItems(key, items);
          if (random.nextBoolean()) {
            writer.cancel(key, OffsetDateTime.now());
          }
        }
      }
    }
  }

  private static List<BatchOperationItemDbModel> newItems(final String key, final int n) {
    final List<BatchOperationItemDbModel> items = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      final long itemKey = nextKey();
      items.add(
          new BatchOperationItemDbModel(
              key,
              itemKey,
              itemKey + 10_000L,
              itemKey + 20_000L,
              BatchOperationItemState.ACTIVE,
              NOW,
              null));
    }
    return items;
  }

  private enum Workload {
    SUSPEND_RESUME,
    MIXED,
    CANCEL_VS_ITEMS,
    SINGLE_CREATE,
    CREATE_EXISTING
  }

  private static final class Result {
    final AtomicInteger deadlocks = new AtomicInteger();
    final AtomicInteger otherFailures = new AtomicInteger();
    final AtomicInteger unfinishedPartitions = new AtomicInteger();
    final AtomicInteger directWriterFailures = new AtomicInteger();

    void add(final Result other) {
      deadlocks.addAndGet(other.deadlocks.get());
      otherFailures.addAndGet(other.otherFailures.get());
      unfinishedPartitions.addAndGet(other.unfinishedPartitions.get());
      directWriterFailures.addAndGet(other.directWriterFailures.get());
    }
  }
}
