/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.cluster.PartitionId;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueOrdering;
import io.camunda.exporter.rdbms.RdbmsExporter;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.mockito.Mockito;

/**
 * The real RDBMS exporter of one partition. Records go through the real export handlers into the
 * real execution queue; the queue is only written when the test calls {@link #flush()}, so the test
 * decides which thread flushes and when. Pass {@code exporter::flush} to {@link
 * RdbmsConcurrencyKit#start} to flush on a controlled thread.
 *
 * <p>Like in production, the exporter catches a failed flush and keeps the records for the next
 * flush: read the failure from {@link ThreadControl#error()}, not from an exception. {@link
 * #export} may also flush on the calling thread when the queue exceeds its memory limit (large
 * seeds).
 */
public final class ControllableExporter {

  private final int partitionId;
  private final SqlSessionFactory sqlSessionFactory;
  private final RdbmsExporterWrapper exporter;
  private final ExporterTestController controller = new ExporterTestController();

  public ControllableExporter(
      final CamundaRdbmsTestApplication app,
      final SqlSessionFactory sqlSessionFactory,
      final int partitionId,
      final Map<String, Object> args) {
    this.partitionId = partitionId;
    this.sqlSessionFactory = sqlSessionFactory;
    final var arguments = new HashMap<String, Object>(args);
    // never flush on our own: the test flushes explicitly
    arguments.putIfAbsent("queueSize", 1_000_000);
    arguments.putIfAbsent("flushInterval", "PT1H");
    final var configuration = new ExporterConfiguration("concurrency-" + partitionId, arguments);
    exporter =
        new RdbmsExporterWrapper(
            app.bean(RdbmsServiceFactory.class),
            app.bean(RdbmsSchemaManagerRegistry.class),
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                configuration.instantiate(io.camunda.exporter.rdbms.ExporterConfiguration.class)));
    exporter.configure(
        new ExporterContext(
            null,
            configuration,
            new PartitionId(DEFAULT_PHYSICAL_TENANT_ID, partitionId),
            "",
            null,
            Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS),
            InstantSource.system()));
    exporter.open(controller);
  }

  int partitionId() {
    return partitionId;
  }

  /** Queues the records; nothing is written yet. */
  public void export(final Record<?>... records) {
    for (final Record<?> record : records) {
      exporter.export(record);
    }
  }

  /**
   * Exports and writes root process instances, each with element instances; returns the root keys.
   * Run it before starting controlled threads.
   */
  public List<Long> seedRootsWithElements(final int roots, final int elementsPerRoot) {
    return seed(
        roots,
        root -> {
          for (int e = 0; e < elementsPerRoot; e++) {
            export(
                KitRecords.elementActivating(partitionId, KitRecords.nextKey(partitionId), root));
          }
        });
  }

  /**
   * Exports and writes root process instances, each with child process instances; returns the root
   * keys. Run it before starting controlled threads.
   */
  public List<Long> seedRootsWithChildren(final int roots, final int childrenPerRoot) {
    return seed(
        roots,
        root -> {
          for (int c = 0; c < childrenPerRoot; c++) {
            export(
                KitRecords.childProcessStarted(partitionId, KitRecords.nextKey(partitionId), root));
          }
        });
  }

  /**
   * Exports and writes an active batch operation (created and initialized); returns its key. Run it
   * before starting controlled threads.
   */
  public long seedBatchOperation() {
    final long batchOperation = KitRecords.nextKey(partitionId);
    export(
        KitRecords.batchOperationCreated(partitionId, batchOperation),
        KitRecords.batchOperationInitialized(partitionId, batchOperation));
    flush();
    return batchOperation;
  }

  /**
   * Exports and writes an active batch operation with a chunk of items; returns its key. Run it
   * before starting controlled threads.
   */
  public long seedBatchOperationWithItems() {
    final long batchOperation = seedBatchOperation();
    export(KitRecords.batchOperationChunkCreated(partitionId, batchOperation));
    flush();
    return batchOperation;
  }

  /**
   * Exports and writes an active batch operation with the given number of items; returns its key.
   * Run it before starting controlled threads.
   */
  public long seedBatchOperationWithItems(final int items) {
    final long batchOperation = seedBatchOperation();
    // chunks of 100 items, the engine's default chunk size
    for (int exported = 0; exported < items; exported += 100) {
      export(
          KitRecords.batchOperationChunkCreated(
              partitionId, batchOperation, Math.min(100, items - exported)));
      if (exported % 5_000 == 4_900) {
        flush();
      }
    }
    flush();
    return batchOperation;
  }

  private List<Long> seed(final int roots, final LongConsumer perRoot) {
    final List<Long> keys = new ArrayList<>();
    for (int r = 0; r < roots; r++) {
      final long root = KitRecords.nextKey(partitionId);
      keys.add(root);
      export(KitRecords.processStarted(partitionId, root));
      perRoot.accept(root);
      if (r % 200 == 199) {
        flush();
      }
    }
    flush();
    return keys;
  }

  /**
   * Writes the queue in one flush transaction, on the calling thread.
   *
   * @throws IllegalStateException if the flush failed: the exporter only logs a failed flush and
   *     keeps the statements queued, which would otherwise leave a test running on data that was
   *     never written
   */
  public void flush() {
    controller.runScheduledTasks(Duration.ofHours(2));
    final int left;
    try {
      left = queuedItems().size();
    } catch (final ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    if (left > 0) {
      throw new IllegalStateException(
          "P"
              + partitionId
              + " flush failed, "
              + left
              + " statements are still queued; the exporter logged the error (\"Error while"
              + " executing queue\")");
    }
  }

  /**
   * Flushes the queued records with the given tables (context types) first, simulating <b>another
   * broker</b>.
   *
   * <p>The real flush groups the queue by {@link ContextType} into a {@code HashMap}, so the table
   * order is the map's iteration order, which follows the enum constants' identity hash codes:
   * fixed within one JVM, different between brokers. Two exporters in one test JVM therefore always
   * write tables in the same order. This method writes the same queued statements, with the same
   * parameters and the same order within each table, in one transaction like the real flush, but
   * with the given tables first. It does not update the exporter position.
   */
  public void flushInTableOrder(final List<ContextType> first) throws Exception {
    final Map<ContextType, List<QueueItem>> byTable = new LinkedHashMap<>();
    for (final ContextType table : first) {
      byTable.put(table, new ArrayList<>());
    }
    for (final QueueItem item : drainQueue()) {
      byTable.computeIfAbsent(item.contextType(), t -> new ArrayList<>()).add(item);
    }
    try (final SqlSession session =
        sqlSessionFactory.openSession(
            ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED)) {
      for (final var table : byTable.entrySet()) {
        for (final QueueItem item : inTableOrder(table.getKey(), table.getValue())) {
          session.update(item.statementId(), item.parameter());
        }
      }
      session.commit();
    }
  }

  /**
   * As {@code QueueOrdering}, whose {@code apply} is not visible here; other orderings keep arrival
   * order.
   */
  private static List<QueueItem> inTableOrder(
      final ContextType table, final List<QueueItem> items) {
    return table.ordering() != QueueOrdering.SORT_BY_STATEMENT_TYPE
        ? items
        : items.stream()
            .sorted(
                Comparator.comparing(QueueItem::statementType)
                    .thenComparingInt(QueueItem::order)
                    .thenComparing(QueueItem::statementId))
            .toList();
  }

  /**
   * The given tables in the reverse of the order in which a real flush of this JVM writes them: the
   * order of a broker whose JVM orders them the other way, for {@link #flushInTableOrder}.
   */
  public static List<ContextType> otherBrokersTableOrder(final ContextType... tables) {
    // the same grouping as DefaultExecutionQueue#optimizeQueueOrder
    return List.copyOf(
            Stream.of(tables).collect(Collectors.groupingBy(Function.identity())).keySet())
        .reversed();
  }

  private List<QueueItem> drainQueue() throws ReflectiveOperationException {
    final var items = queuedItems();
    final List<QueueItem> drained = List.copyOf(items);
    items.clear();
    return drained;
  }

  /** The exporter's queue itself, not a copy. */
  private List<QueueItem> queuedItems() throws ReflectiveOperationException {
    final Field exporterField = RdbmsExporterWrapper.class.getDeclaredField("exporter");
    exporterField.setAccessible(true);
    final Field writersField = RdbmsExporter.class.getDeclaredField("rdbmsWriters");
    writersField.setAccessible(true);
    final var writers = (RdbmsWriters) writersField.get(exporterField.get(exporter));
    final var queue = writers.getExecutionQueue();
    final Method getQueue = queue.getClass().getDeclaredMethod("getQueue");
    getQueue.setAccessible(true);
    @SuppressWarnings("unchecked")
    final var items = (List<QueueItem>) getQueue.invoke(queue);
    return items;
  }

  public void close() {
    exporter.close();
  }
}
