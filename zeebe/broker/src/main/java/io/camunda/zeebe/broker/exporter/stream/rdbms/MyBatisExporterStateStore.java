/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream.rdbms;

import io.camunda.zeebe.broker.exporter.stream.ExporterStateEntry;
import io.camunda.zeebe.broker.exporter.stream.ExporterStateStore;
import io.camunda.zeebe.broker.exporter.stream.rdbms.ExporterStateMapper.ExporterStateRow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the exporter state of a partition in the relational database of the primary storage.
 *
 * <p>Exporters acknowledge a position after every record, so the store holds the updates back and
 * writes the latest one of each exporter in a single transaction when it is {@link #flush()
 * flushed}. Reads see the updates it holds. An exporter delivers at least once, so a position
 * stored late only makes the next leader export the records after it again; the stored positions
 * never get ahead of what was exported. Removing an exporter's state is written at once.
 *
 * <p>Only the exporter director of the partition's leader writes the state, and it needs no
 * transaction with the engine state.
 */
public final class MyBatisExporterStateStore implements ExporterStateStore {

  private static final Logger LOG = LoggerFactory.getLogger(MyBatisExporterStateStore.class);

  private final SqlSessionFactory sessionFactory;
  private final int partitionId;
  private final Map<String, ExporterStateRow> pending = new ConcurrentHashMap<>();

  public MyBatisExporterStateStore(final SqlSessionFactory sessionFactory, final int partitionId) {
    this.sessionFactory = sessionFactory;
    this.partitionId = partitionId;
  }

  /**
   * Builds the factory of the sessions of all partitions' stores, on a pool whose schema was
   * migrated with {@link ExporterStateSchema#CHANGELOG}. Build it once per pool.
   */
  public static SqlSessionFactory createSessionFactory(final DataSource dataSource) {
    final var configuration =
        new Configuration(
            new Environment("exporter-state", new JdbcTransactionFactory(), dataSource));
    final var resource = ExporterStateSchema.MAPPER;
    try (final var mapperXml =
        MyBatisExporterStateStore.class.getClassLoader().getResourceAsStream(resource)) {
      if (mapperXml == null) {
        throw new IllegalStateException("Missing MyBatis mapper resource " + resource);
      }
      new XMLMapperBuilder(mapperXml, configuration, resource, configuration.getSqlFragments())
          .parse();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  @Override
  public Optional<ExporterStateEntry> find(final String exporterId) {
    final var held = pending.get(exporterId);
    if (held != null) {
      return Optional.of(toEntry(held));
    }
    return inTransaction(
        mapper ->
            Optional.ofNullable(mapper.selectExporterState(partitionId, exporterId))
                .map(MyBatisExporterStateStore::toEntry));
  }

  /** Holds the update back until the next {@link #flush()}. */
  @Override
  public void upsert(final String exporterId, final ExporterStateEntry entry) {
    pending.put(exporterId, toRow(exporterId, entry));
  }

  @Override
  public void delete(final String exporterId) {
    pending.remove(exporterId);
    write(mapper -> mapper.deleteExporterState(partitionId, exporterId));
  }

  @Override
  public void forEach(final BiConsumer<String, ExporterStateEntry> consumer) {
    final Map<String, ExporterStateRow> rows = new TreeMap<>();
    inTransaction(mapper -> mapper.selectExporterStates(partitionId))
        .forEach(row -> rows.put(row.exporterId(), row));
    rows.putAll(pending);
    rows.forEach((exporterId, row) -> consumer.accept(exporterId, toEntry(row)));
  }

  @Override
  public boolean isEmpty() {
    return pending.isEmpty()
        && inTransaction(mapper -> mapper.countExporterStates(partitionId)) == 0;
  }

  /**
   * Writes the held updates in one transaction. If that fails, they stay held for the next flush.
   * An update that arrives while flushing stays held too, since only the written ones are let go.
   */
  @Override
  public void flush() {
    if (pending.isEmpty()) {
      return;
    }
    final var rows = Map.copyOf(pending);
    try {
      write(
          mapper ->
              rows.values()
                  .forEach(
                      row -> {
                        if (mapper.updateExporterState(partitionId, row) == 0) {
                          mapper.insertExporterState(partitionId, row);
                        }
                      }));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to store the positions of {} exporters of partition {}, retrying with the next "
              + "flush",
          rows.size(),
          partitionId,
          e);
      return;
    }
    rows.forEach(pending::remove);
  }

  private void write(final Consumer<ExporterStateMapper> operation) {
    try (final var session = sessionFactory.openSession(false)) {
      operation.accept(session.getMapper(ExporterStateMapper.class));
      session.commit(true);
    }
  }

  private <T> T inTransaction(final Function<ExporterStateMapper, T> operation) {
    try (final var session = sessionFactory.openSession(false)) {
      final T result = operation.apply(session.getMapper(ExporterStateMapper.class));
      session.commit(true);
      return result;
    }
  }

  private static ExporterStateRow toRow(final String exporterId, final ExporterStateEntry entry) {
    final var metadata = entry.getMetadata();
    final var bytes = new byte[metadata.capacity()];
    metadata.getBytes(0, bytes);
    return new ExporterStateRow(exporterId, entry.getPosition(), entry.getMetadataVersion(), bytes);
  }

  private static ExporterStateEntry toEntry(final ExporterStateRow row) {
    return new ExporterStateEntry()
        .setPosition(row.position())
        .setMetadataVersion(row.metadataVersion())
        .setMetadata(new UnsafeBuffer(row.metadata()));
  }
}
