/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import liquibase.Scope;
import liquibase.analytics.configuration.AnalyticsArgs;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The key generator keeps the latest key of a partition in the database, so that whoever leads the
 * partition next, in this or another region, continues after it.
 *
 * <p>Runs on H2, so it needs no container. {@code NON_KEYWORDS=VALUE} lets H2 accept the {@code
 * value} column of the partition state table.
 */
final class RdbmsKeyGeneratorTest {

  private static final AtomicInteger PARTITION_IDS = new AtomicInteger();
  private static JdbcDataSource dataSource;
  private static SqlSessionFactory sessionFactory;

  private int partitionId;

  @BeforeAll
  static void createDatabase() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:key-generator-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    try (final var database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()))) {
      database.setDatabaseChangeLogTableName(RdbmsEngineStateSchema.DATABASE_CHANGELOG_TABLE);
      database.setDatabaseChangeLogLockTableName(
          RdbmsEngineStateSchema.DATABASE_CHANGELOG_LOCK_TABLE);
      Scope.child(
          Map.of(
              Scope.Attr.resourceAccessor.name(),
              new ClassLoaderResourceAccessor(),
              AnalyticsArgs.ENABLED.getKey(),
              false),
          () ->
              new CommandScope(UpdateCommandStep.COMMAND_NAME)
                  .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                  .addArgumentValue(
                      UpdateCommandStep.CHANGELOG_FILE_ARG, RdbmsEngineStateSchema.CHANGELOG)
                  .execute());
    }
    sessionFactory = EngineStateMyBatis.createSqlSessionFactory(dataSource);
  }

  @BeforeEach
  void usePartitionOfItsOwn() {
    partitionId = PARTITION_IDS.incrementAndGet();
  }

  @Test
  void shouldGenerateTheFirstKeyOfThePartition() {
    // given
    final var context = new JdbcTransactionContext();

    // when
    final long key = inTransaction(context, () -> keyGenerator(context).nextKey());

    // then
    assertThat(key).isEqualTo(Protocol.encodePartitionId(partitionId, 1));
  }

  @Test
  void shouldContinueAfterTheLatestKeyOfAnotherNode() {
    // given - the former leader generated keys
    final var formerLeader = new JdbcTransactionContext();
    final long latestKey =
        inTransaction(
            formerLeader,
            () -> {
              final var keys = keyGenerator(formerLeader);
              keys.nextKey();
              keys.nextKey();
              return keys.nextKey();
            });

    // when - the new leader, for example in another region, starts with a context of its own
    final var newLeader = new JdbcTransactionContext();
    final long nextKey = inTransaction(newLeader, () -> keyGenerator(newLeader).nextKey());

    // then
    assertThat(nextKey).isEqualTo(latestKey + 1);
  }

  @Test
  void shouldNotKeepTheKeysOfATransactionThatRolledBack() throws Exception {
    // given
    final var context = new JdbcTransactionContext();
    final long committedKey = inTransaction(context, () -> keyGenerator(context).nextKey());
    final var transaction = context.getCurrentTransaction();
    transaction.run(() -> keyGenerator(context).nextKey());

    // when
    transaction.rollback();

    // then
    final var other = new JdbcTransactionContext();
    assertThat(inTransaction(other, () -> keyGenerator(other).getCurrentKey()))
        .isEqualTo(committedKey);
  }

  @Test
  void shouldRaiseTheKeyOnlyIfItIsHigher() {
    // given
    final var context = new JdbcTransactionContext();
    final long higherKey = Protocol.encodePartitionId(partitionId, 100);
    inTransaction(
        context,
        () -> {
          keyGenerator(context).setKeyIfHigher(higherKey);
          return 0L;
        });

    // when
    final long currentKey =
        inTransaction(
            context,
            () -> {
              final var keys = keyGenerator(context);
              keys.setKeyIfHigher(Protocol.encodePartitionId(partitionId, 5));
              return keys.getCurrentKey();
            });

    // then
    assertThat(currentKey).isEqualTo(higherKey);
    assertThat(storedKey()).isEqualTo(higherKey);
  }

  @Test
  void shouldRejectAKeyOfAnotherPartition() {
    // given
    final var context = new JdbcTransactionContext();
    final long keyOfAnotherPartition = Protocol.encodePartitionId(partitionId + 1000, 7);

    // when / then
    assertThatThrownBy(
            () ->
                inTransaction(
                    context,
                    () -> {
                      keyGenerator(context).setKeyIfHigher(keyOfAnotherPartition);
                      return 0L;
                    }))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .isInstanceOf(UnrecoverableException.class);
  }

  @Test
  void shouldShareTheSessionOfTheContextWithTheStateStores() {
    // given
    final var context = new JdbcTransactionContext();

    // when
    final var first = EntitySession.of(context, partitionId, sessionFactory);
    final var second = EntitySession.of(context, partitionId, sessionFactory);

    // then
    assertThat(second).isSameAs(first);
    assertThatThrownBy(() -> EntitySession.of(context, partitionId + 1, sessionFactory))
        .isInstanceOf(IllegalStateException.class);
  }

  private RdbmsKeyGenerator keyGenerator(final JdbcTransactionContext context) {
    return new RdbmsKeyGenerator(EntitySession.of(context, partitionId, sessionFactory));
  }

  private static long inTransaction(
      final JdbcTransactionContext context, final LongSupplier operation) {
    final var transaction = context.getCurrentTransaction();
    final long[] result = new long[1];
    try {
      transaction.run(() -> result[0] = operation.getAsLong());
      transaction.commit();
      return result[0];
    } catch (final Exception e) {
      rollBack(transaction);
      if (e instanceof final RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(e);
    }
  }

  private static void rollBack(final ZeebeDbTransaction transaction) {
    try {
      transaction.rollback();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private long storedKey() {
    try (final var connection = dataSource.getConnection();
        final var statement =
            connection.prepareStatement(
                "SELECT value FROM CAM_RU_PARTITION_STATE WHERE partition_id = ? AND state_key = ?")) {
      statement.setInt(1, partitionId);
      statement.setString(2, RdbmsKeyGenerator.LATEST_KEY);
      try (final var result = statement.executeQuery()) {
        assertThat(result.next()).as("the latest key is stored").isTrue();
        return Long.parseLong(result.getString(1));
      }
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
