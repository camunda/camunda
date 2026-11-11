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
import java.util.Optional;
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

/**
 * Keeps the exporter state of a partition in the relational database of the primary storage. Each
 * operation is one transaction of its own: only the exporter director of the partition's leader
 * writes it, and the state needs no transaction with the engine state.
 */
public final class MyBatisExporterStateStore implements ExporterStateStore {

  private final SqlSessionFactory sessionFactory;
  private final int partitionId;

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
    return inTransaction(
        mapper ->
            Optional.ofNullable(mapper.selectExporterState(partitionId, exporterId))
                .map(MyBatisExporterStateStore::toEntry));
  }

  /** Updates the row, or inserts it if there is none: only one exporter director writes it. */
  @Override
  public void upsert(final String exporterId, final ExporterStateEntry entry) {
    final var row = toRow(exporterId, entry);
    write(
        mapper -> {
          if (mapper.updateExporterState(partitionId, row) == 0) {
            mapper.insertExporterState(partitionId, row);
          }
        });
  }

  @Override
  public void delete(final String exporterId) {
    write(mapper -> mapper.deleteExporterState(partitionId, exporterId));
  }

  @Override
  public void forEach(final BiConsumer<String, ExporterStateEntry> consumer) {
    inTransaction(mapper -> mapper.selectExporterStates(partitionId))
        .forEach(row -> consumer.accept(row.exporterId(), toEntry(row)));
  }

  @Override
  public boolean isEmpty() {
    return inTransaction(mapper -> mapper.countExporterStates(partitionId)) == 0;
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
