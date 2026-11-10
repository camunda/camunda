/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.configuration.RdbmsEngineStateCfg;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.jdbc.MixedZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental PoC: opens the JDBC connection for the mixed RocksDB/RDBMS engine state of a
 * partition, creates the schema and wipes the partition's rows, since restarts are not supported.
 */
final class RdbmsEngineStateDb {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsEngineStateDb.class);
  private static final String DDL_RESOURCE = "db/engine-state/postgres/element_instance.sql";

  /**
   * Only the tables our own DDL creates are wiped: other tables in the same schema, e.g. the RDBMS
   * exporter's, have partition_id columns too.
   */
  private static final Pattern CREATE_TABLE =
      Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");

  private RdbmsEngineStateDb() {}

  static MixedZeebeDb<ZbColumnFamilies> open(
      final ZeebeDb<ZbColumnFamilies> rocksDb,
      final RdbmsEngineStateCfg cfg,
      final int partitionId) {
    LOG.warn(
        "EXPERIMENTAL: partition {} stores element instances in {} (mixed RocksDB/RDBMS engine "
            + "state). All rows of this partition are deleted on every start. Restarts and "
            + "replication factor > 1 are NOT supported.",
        partitionId,
        cfg.getUrl());

    final Connection connection;
    try {
      connection = DriverManager.getConnection(cfg.getUrl(), cfg.getUsername(), cfg.getPassword());
    } catch (final SQLException e) {
      throw new IllegalStateException(
          "Failed to connect to the engine state database " + cfg.getUrl(), e);
    }

    try {
      final var tables = createSchema(connection);
      deletePartitionRows(connection, partitionId, tables);
    } catch (final SQLException | IOException e) {
      try {
        connection.close();
      } catch (final SQLException closeError) {
        e.addSuppressed(closeError);
      }
      throw new IllegalStateException(
          "Failed to prepare the engine state database for partition " + partitionId, e);
    }
    return new MixedZeebeDb<>(rocksDb, connection);
  }

  /** Executes the DDL and returns the names of the tables it creates. */
  private static List<String> createSchema(final Connection connection)
      throws SQLException, IOException {
    final String ddl;
    try (final InputStream in =
        Objects.requireNonNull(
            RdbmsEngineStateDb.class.getClassLoader().getResourceAsStream(DDL_RESOURCE),
            "Missing DDL resource " + DDL_RESOURCE)) {
      ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    final var withoutComments = ddl.replaceAll("(?m)^\\s*--.*$", "");
    final List<String> tables = new ArrayList<>();
    try (final var statement = connection.createStatement()) {
      for (final String sql : withoutComments.split(";")) {
        if (!sql.isBlank()) {
          statement.execute(sql);
          final var createTable = CREATE_TABLE.matcher(sql);
          if (createTable.find()) {
            tables.add(createTable.group(1));
          }
        }
      }
    }
    return tables;
  }

  private static void deletePartitionRows(
      final Connection connection, final int partitionId, final List<String> tables)
      throws SQLException {
    for (final String table : tables) {
      try (final var delete =
          connection.prepareStatement("DELETE FROM " + table + " WHERE partition_id = ?")) {
        delete.setInt(1, partitionId);
        final int deleted = delete.executeUpdate();
        LOG.info("Deleted {} rows of partition {} from table {}", deleted, partitionId, table);
      }
    }
  }
}
