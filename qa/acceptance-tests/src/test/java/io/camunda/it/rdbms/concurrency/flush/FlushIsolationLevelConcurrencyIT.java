/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.flush;

import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.processStarted;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.insert;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The flush opens its session with {@code
 * sessionFactory.openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED)} ({@code
 * DefaultExecutionQueue}). The session factory is built by MyBatis-Spring's {@code
 * SqlSessionFactoryBean} without a transaction factory, so it uses {@code
 * SpringManagedTransactionFactory}, whose {@code newTransaction(dataSource, level, autoCommit)}
 * ignores the level. The connection pool sets no isolation either, so the flush runs at the
 * database's default.
 *
 * <p><b>How.</b> The flush stops before its insert, with its transaction open (it already locked
 * and updated its exporter position). The test reads that transaction's isolation level from the
 * database, over another connection: {@code information_schema.innodb_trx} on MySQL and MariaDB,
 * {@code sys.dm_exec_sessions} on SQL Server. PostgreSQL and Oracle do not show another session's
 * isolation level; their default is READ COMMITTED anyway.
 *
 * <p><b>Today.</b> MySQL and MariaDB: REPEATABLE READ, the InnoDB default. SQL Server: READ
 * COMMITTED, its default.
 *
 * <p><b>Fix direction.</b> Set the isolation level on the connection pool ({@code
 * HikariDataSource#setTransactionIsolation("TRANSACTION_READ_COMMITTED")} in {@code
 * RdbmsDataSources}).
 */
@RdbmsConcurrencyTest(databases = {MYSQL, MARIADB, MSSQL})
class FlushIsolationLevelConcurrencyIT {

  @TestTemplate
  @FailsToday(
      on = {MYSQL, MARIADB},
      reason =
          "MyBatis-Spring ignores the READ COMMITTED the flush asks for; it runs at REPEATABLE READ")
  void shouldFlushAtReadCommitted(final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: a flush with a new process instance
      final var p1 = kit.exporter(1);
      p1.export(processStarted(1, nextKey(1)));
      final var flush = kit.startFlush(p1);

      // when: the flush stops before its insert, with its transaction open
      flush.stepTo(insert("PROCESS_INSTANCE"));
      final var isolationLevels = kit.adminQuery(openTransactionsIsolationLevel(kit));
      flush.release();

      // then: the flush's transaction (the only open one) runs at READ COMMITTED
      assertThat(isolationLevels)
          .describedAs("isolation level of the open flush transaction")
          .containsExactly("READ COMMITTED");
      kit.assertNoErrors(flush);
    }
  }

  /** The isolation level of every open transaction, as the database reports it. */
  private static String openTransactionsIsolationLevel(final RdbmsConcurrencyKit kit) {
    return switch (kit.database()) {
      case MYSQL, MARIADB -> "SELECT trx_isolation_level FROM information_schema.innodb_trx";
      case MSSQL ->
          """
          SELECT CASE s.transaction_isolation_level
                   WHEN 1 THEN 'READ UNCOMMITTED' WHEN 2 THEN 'READ COMMITTED'
                   WHEN 3 THEN 'REPEATABLE READ' WHEN 4 THEN 'SERIALIZABLE' WHEN 5 THEN 'SNAPSHOT'
                 END
          FROM sys.dm_exec_sessions s
          JOIN sys.dm_tran_session_transactions t ON t.session_id = s.session_id
          WHERE s.session_id <> @@SPID
          """;
      default -> throw new IllegalStateException("not observable on " + kit.database());
    };
  }
}
