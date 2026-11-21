/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Entry point of the RDBMS concurrency kit (see README.md next to this class): real exporters of
 * several partitions and real history cleanup/deletion statements, each on a {@link ThreadControl}
 * thread that the test drives statement by statement, against one test database. Reads the
 * database's own lock and deadlock evidence for assertion messages.
 */
public final class RdbmsConcurrencyKit implements AutoCloseable {

  private final CamundaRdbmsTestApplication app;
  private final SqlSessionFactory sqlSessionFactory;
  private final RdbmsService rdbmsService;
  private final Database database;
  private final String adminUrl;
  private final List<ControllableExporter> exporters = new ArrayList<>();
  private final List<ThreadControl> threads = new ArrayList<>();

  public RdbmsConcurrencyKit(final CamundaRdbmsTestApplication app) throws Exception {
    this.app = app;
    final var factory = app.bean(RdbmsServiceFactory.class);
    final Field bundles = RdbmsServiceFactory.class.getDeclaredField("rdbmsMapperBundles");
    bundles.setAccessible(true);
    @SuppressWarnings("unchecked")
    final var mapperBundles = (Map<String, RdbmsMapperBundle>) bundles.get(factory);
    sqlSessionFactory = mapperBundles.get(DEFAULT_PHYSICAL_TENANT_ID).sqlSessionFactory();
    final var configuration = sqlSessionFactory.getConfiguration();
    if (configuration.getInterceptors().stream()
        .noneMatch(ControlledStatements.class::isInstance)) {
      configuration.addInterceptor(new ControlledStatements());
    }
    rdbmsService =
        factory.createRdbmsService(DEFAULT_PHYSICAL_TENANT_ID, new SimpleMeterRegistry());
    try (final SqlSession s = sqlSessionFactory.openSession()) {
      final var meta = s.getConnection().getMetaData();
      database = Database.ofProductName(meta.getDatabaseProductName());
      adminUrl =
          meta.getURL()
              .replaceAll("([?&])(user|password)=[^&]*", "$1")
              .replaceAll("\\?&", "?")
              .replaceAll("[?&]$", "");
    }
    if (database == Database.MSSQL
        && "true".equals(System.getenv("RDBMS_KIT_MSSQL_RCSI"))
        && count("SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = DB_NAME()")
            != 1) {
      throw new IllegalStateException(
          "RDBMS_KIT_MSSQL_RCSI is set, but READ_COMMITTED_SNAPSHOT is off");
    }
  }

  public Database database() {
    return database;
  }

  /** The real exporter of a partition, with the default configuration. */
  public ControllableExporter exporter(final int partitionId) {
    return exporter(partitionId, Map.of());
  }

  /** The real exporter of a partition; arguments as in the exporter configuration. */
  public ControllableExporter exporter(final int partitionId, final Map<String, Object> args) {
    final var exporter = new ControllableExporter(app, sqlSessionFactory, partitionId, args);
    exporters.add(exporter);
    return exporter;
  }

  /** Starts the exporter's flush on a controlled thread named "P&lt;partition&gt; flush". */
  public ThreadControl startFlush(final ControllableExporter exporter) {
    return start("P" + exporter.partitionId() + " flush", exporter::flush);
  }

  /**
   * Starts a history cleanup or deletion statement of a partition on a controlled thread named
   * "P&lt;partition&gt; cleanup", with the writers {@code HistoryCleanupService} uses, e.g. {@code
   * kit.startCleanup(2, writers -> writers.getFlowNodeInstanceWriter()
   * .deleteRootProcessInstanceRelatedData(roots, 10_000))}. Each statement commits on its own, as
   * in production.
   */
  public ThreadControl startCleanup(final int partitionId, final WritersWork work) {
    final RdbmsWriters writers = createWriters(partitionId);
    return start("P" + partitionId + " cleanup", () -> work.run(writers));
  }

  private RdbmsWriters createWriters(final int partitionId) {
    return rdbmsService.createWriter(
        RdbmsWriterConfig.builder()
            .partitionId(partitionId)
            .history(HistoryConfig.builder().build())
            .build());
  }

  /**
   * Starts writes through a partition's writers on a controlled thread named "P&lt;partition&gt;
   * flush": the work queues them, then the writers flush them in one transaction, as the exporter
   * does. The exporter's handlers call the same writers, so the flush runs the exporter's SQL, e.g.
   * {@code kit.startWrite(1, writers -> writers.getUserTaskWriter().create(task))}.
   */
  public ThreadControl startWrite(final int partitionId, final WritersWork work) {
    final RdbmsWriters writers = createWriters(partitionId);
    return start(
        "P" + partitionId + " flush",
        () -> {
          work.run(writers);
          writers.flush();
        });
  }

  /**
   * Test setup with a partition's writers, on the calling thread (not controlled): runs the work,
   * then flushes what it queued, e.g. {@code kit.write(1, writers ->
   * writers.getBatchOperationWriter().scheduleForHistoryCleanup(key, date))}.
   */
  public void write(final int partitionId, final WritersWork work) throws Exception {
    final RdbmsWriters writers = createWriters(partitionId);
    work.run(writers);
    writers.flush();
  }

  /** Starts any work on a controlled thread; it stops before its first statement. */
  public ThreadControl start(final String name, final ThreadControl.Work work) {
    final var control = ThreadControl.start(name, this::lockWaits, work);
    threads.add(control);
    return control;
  }

  /**
   * Waits for the threads to end (release them first) and fails, with each failed thread's {@link
   * #report}, if any did not end or failed.
   */
  public void assertNoErrors(final ThreadControl... threads) throws InterruptedException {
    final List<String> failures = new ArrayList<>();
    for (final ThreadControl thread : threads) {
      if (!thread.waitUntilDone()) {
        failures.add(thread.name() + " did not end within 60 s (released?)\n" + thread.report());
      } else if (thread.error() != null) {
        failures.add(report(thread));
      }
    }
    assertThat(failures).describedAs(String.join("\n\n", failures)).isEmpty();
  }

  /** Work with a partition's writers: cleanup or deletion statements, or writes to flush. */
  @FunctionalInterface
  public interface WritersWork {
    void run(RdbmsWriters writers) throws Exception;
  }

  /** Lets every controlled thread finish and closes the exporters. */
  @Override
  public void close() throws InterruptedException {
    threads.forEach(ThreadControl::release);
    for (final ThreadControl t : threads) {
      t.waitUntilDone();
    }
    exporters.forEach(ControllableExporter::close);
  }

  /**
   * Refreshes the query planner's statistics of the tables, as a long-running installation has
   * them. Right after seeding, a database may still plan with the statistics of a near-empty table
   * (MySQL: a full primary key scan instead of a key lookup), which locks far more than in
   * production; when that may decide a scenario, analyze after seeding.
   */
  public void analyze(final String... tables) throws SQLException {
    try (final SqlSession s = sqlSessionFactory.openSession(true);
        final Statement st = s.getConnection().createStatement()) {
      for (final String table : tables) {
        st.execute(
            switch (database) {
              case MYSQL, MARIADB, H2 -> "ANALYZE TABLE " + table;
              case POSTGRES -> "ANALYZE " + table;
              case ORACLE -> "BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, '" + table + "'); END;";
              case MSSQL -> "UPDATE STATISTICS " + table;
            });
      }
    }
  }

  public long count(final String sql) throws SQLException {
    try (final SqlSession s = sqlSessionFactory.openSession(true);
        final Statement st = s.getConnection().createStatement();
        final ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  /** The rows of a query, each column as a string. */
  private List<String[]> query(final String sql) throws SQLException {
    try (final SqlSession s = sqlSessionFactory.openSession(true);
        final Statement st = s.getConnection().createStatement();
        final ResultSet rs = st.executeQuery(sql)) {
      final int n = rs.getMetaData().getColumnCount();
      final List<String[]> result = new ArrayList<>();
      while (rs.next()) {
        final String[] row = new String[n];
        for (int i = 0; i < n; i++) {
          row[i] = rs.getString(i + 1);
        }
        result.add(row);
      }
      return result;
    }
  }

  /**
   * SQL Server only: two rows of a partition on neighbouring pages of a table's clustered index,
   * the last row of a page ({@code lower}) and the first row of the next page ({@code upper}),
   * mid-range. A scan in key order reads the lower page right before the upper one. The table needs
   * a {@code <TABLE>_KEY} primary key and a {@code PROCESS_INSTANCE_KEY} column.
   */
  public NeighbouringRows neighbouringRows(final String table, final int partitionId)
      throws SQLException {
    final List<String[]> rows =
        query(
            "SELECT "
                + table
                + "_KEY, PROCESS_INSTANCE_KEY, sys.fn_PhysLocFormatter(%%physloc%%) FROM "
                + table
                + " WHERE "
                + table
                + "_KEY BETWEEN "
                + KitRecords.key(partitionId, 0)
                + " AND "
                + KitRecords.key(partitionId + 1, 0)
                + " ORDER BY 1");
    for (int i = rows.size() / 2; i + 1 < rows.size(); i++) {
      final Row lower = Row.of(rows.get(i));
      final Row upper = Row.of(rows.get(i + 1));
      if (!lower.page().equals(upper.page())) {
        return new NeighbouringRows(lower, upper);
      }
    }
    throw new IllegalStateException("no page boundary in the second half of " + table);
  }

  /** Two rows on neighbouring pages, see {@link #neighbouringRows}. */
  public record NeighbouringRows(Row lower, Row upper) {
    @Override
    public String toString() {
      return "lower " + lower + ", upper " + upper;
    }
  }

  /** A row with its page, e.g. {@code 1:24620}. */
  public record Row(long key, long processInstanceKey, String page) {
    static Row of(final String[] row) {
      // physloc "(1:24620:13)" -> page "1:24620"
      final String physloc = row[2];
      return new Row(
          Long.parseLong(row[0]),
          Long.parseLong(row[1]),
          physloc.substring(1, physloc.lastIndexOf(':')));
    }

    @Override
    public String toString() {
      return key + " on page " + page;
    }
  }

  // ---------------------------------------------------------------- database evidence

  /** The thread's {@link ThreadControl#report()}, plus the database's record of a deadlock. */
  public String report(final ThreadControl thread) {
    final String report = thread.report();
    return thread.error() != null && isDeadlock(thread.error())
        ? report + "\n  database deadlock record:\n" + lastDeadlock(thread.error())
        : report;
  }

  public static boolean isDeadlock(final Throwable t) {
    for (Throwable x = t; x != null; x = x.getCause()) {
      if (x instanceof final SQLException s) {
        final String state = String.valueOf(s.getSQLState());
        if (state.equals("40001")
            || state.equals("40P01")
            || s.getErrorCode() == 1213
            || s.getErrorCode() == 60) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The first column of a query's rows, read over an admin connection, which sees the sessions of
   * the controlled threads, e.g. their open transactions.
   */
  public List<String> adminQuery(final String sql) throws SQLException {
    try (final Connection a = admin();
        final Statement st = a.createStatement();
        final ResultSet rs = st.executeQuery(sql)) {
      final List<String> values = new ArrayList<>();
      while (rs.next()) {
        values.add(rs.getString(1));
      }
      return values;
    }
  }

  /**
   * The current lock waits, as the database reports them; {@link ThreadControl#runTo} adds them to
   * a {@link Progress} that waits for a lock.
   */
  public String lockWaits() {
    final String sql =
        switch (database) {
          case POSTGRES ->
              "SELECT l.locktype, l.mode, c.relname FROM pg_locks l LEFT JOIN pg_class c ON c.oid = l.relation WHERE NOT l.granted";
          case MYSQL ->
              "SELECT rl.OBJECT_NAME, rl.INDEX_NAME, rl.LOCK_MODE, bl.LOCK_MODE blocking_mode, bl.LOCK_DATA blocking_data FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks rl ON rl.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID JOIN performance_schema.data_locks bl ON bl.ENGINE_LOCK_ID = w.BLOCKING_ENGINE_LOCK_ID";
          case MARIADB ->
              "SELECT l.lock_mode, l.lock_type, l.lock_table, l.lock_index, l.lock_data FROM information_schema.innodb_lock_waits w JOIN information_schema.innodb_locks l ON l.lock_id = w.blocking_lock_id";
          case MSSQL ->
              "SELECT t.resource_type, t.request_mode, t.resource_description, w.wait_type, OBJECT_NAME(p.object_id) obj, (SELECT i.name FROM sys.indexes i WHERE i.object_id = p.object_id AND i.index_id = p.index_id) idx FROM sys.dm_tran_locks t LEFT JOIN sys.dm_os_waiting_tasks w ON w.session_id = t.request_session_id LEFT JOIN sys.partitions p ON p.hobt_id = t.resource_associated_entity_id WHERE t.request_status = 'WAIT'";
          case ORACLE ->
              "SELECT s.sid, s.blocking_session, s.event FROM v$session s WHERE s.blocking_session IS NOT NULL";
          case H2 -> null;
        };
    if (sql == null) {
      return "";
    }
    try (final Connection a = admin()) {
      return rows(a, sql);
    } catch (final Exception e) {
      return "(lock waits not readable: " + e.getMessage() + ")";
    }
  }

  /** The database's record of the last deadlock. */
  private String lastDeadlock(final Throwable deadlock) {
    try (final Connection a = admin()) {
      return switch (database) {
        case MYSQL, MARIADB -> {
          final String status = rows(a, "SHOW ENGINE INNODB STATUS");
          final int from = status.indexOf("LATEST DETECTED DEADLOCK");
          final int to = status.indexOf("TRANSACTIONS\n------------", Math.max(from, 0));
          yield from < 0 ? status : status.substring(from, to < 0 ? status.length() : to);
        }
        case MSSQL ->
            rows(
                a,
                "SELECT TOP 1 CAST(q.xed.query('.') AS NVARCHAR(MAX)) FROM (SELECT CAST(t.target_data AS XML) td"
                    + " FROM sys.dm_xe_session_targets t JOIN sys.dm_xe_sessions s ON s.address = t.event_session_address"
                    + " WHERE s.name = 'system_health' AND t.target_name = 'ring_buffer') x"
                    + " CROSS APPLY x.td.nodes('//RingBufferTarget/event[@name=\"xml_deadlock_report\"]') AS q(xed)"
                    + " ORDER BY q.xed.value('(@timestamp)[1]', 'datetime2') DESC");
        default -> String.valueOf(deadlock);
      };
    } catch (final Exception e) {
      return "(deadlock record not readable: " + e.getMessage() + ") " + deadlock;
    }
  }

  private Connection admin() throws SQLException {
    final String user =
        switch (database) {
          case MYSQL, MARIADB -> "root";
          case ORACLE -> "system";
          case MSSQL, H2 -> "sa";
          case POSTGRES -> TestSearchContainers.CAMUNDA_USER;
        };
    final String password = database == Database.H2 ? "" : TestSearchContainers.CAMUNDA_PASSWORD;
    return DriverManager.getConnection(adminUrl, user, password);
  }

  private static String rows(final Connection c, final String sql) throws SQLException {
    try (final Statement st = c.createStatement();
        final ResultSet rs = st.executeQuery(sql)) {
      final StringBuilder sb = new StringBuilder();
      final int n = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        for (int i = 1; i <= n; i++) {
          sb.append(rs.getString(i)).append(i < n ? " | " : "\n");
        }
      }
      return sb.toString();
    }
  }
}
