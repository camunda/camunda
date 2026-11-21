/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.variable;

import static io.camunda.it.rdbms.concurrency.kit.Database.H2;
import static io.camunda.it.rdbms.concurrency.kit.Database.MARIADB;
import static io.camunda.it.rdbms.concurrency.kit.Database.MSSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.MYSQL;
import static io.camunda.it.rdbms.concurrency.kit.Database.ORACLE;
import static io.camunda.it.rdbms.concurrency.kit.Database.POSTGRES;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextKey;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextName;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.variableCreated;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.statement;

import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.concurrency.kit.SyncPoint;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The exporter records every variable name of a process definition in
 * PROCESS_DEF_VAR_NAME_LOOKUP (key: process definition key + name), with an insert-if-not-exists
 * the first time its cache sees the name. The process definition is the same on all partitions, so
 * all partitions write the same rows. A new process version is deployed and instances start on
 * several partitions at once: every partition inserts the same new names. The engine emits the
 * variables of one document in no fixed order (see {@link
 * VariableNameOrderFromEngineConcurrencyIT}), so two partitions can see the names in opposite
 * order.
 *
 * <p><b>Interleaving</b> (first test; time flows down):
 *
 * <pre>
 *   P1 flush                          P2 flush
 *   insert name a   holds a
 *                                     insert name d   holds d
 *   insert name d   waits for P2
 *                                     insert name a   waits for P1: deadlock
 * </pre>
 *
 * <p><b>Today.</b>
 *
 * <ul>
 *   <li>New names in opposite order: deadlock on every database (H2 waits until its lock timeout).
 *   <li>Existing names (e.g. after an exporter restart, with an empty cache), and existing + new
 *       names: no conflict, except on Oracle, see the next item.
 *   <li>One new name at the same time: Oracle's {@code MERGE} is not atomic, the second partition
 *       fails with ORA-00001 on the primary key instead of skipping the row. The flush is retried
 *       and then succeeds, but it fails every time two partitions see a new name together.
 * </ul>
 *
 * <p><b>Fix direction.</b> Insert the names of one flush in a fixed order (sorted), and make the
 * insert tolerate a concurrent duplicate on Oracle.
 */
@RdbmsConcurrencyTest
class VariableNameLookupConcurrencyIT {

  private static final SyncPoint.Match INSERT_NAME =
      statement("VariableMapper.insertLookupIfNotExists");

  @TestTemplate
  @FailsToday(
      on = {POSTGRES, MYSQL, MARIADB, ORACLE, MSSQL},
      reason = "each flush holds the new name it inserted first and needs the other one: deadlock")
  @FailsToday(
      on = H2,
      reason = "H2 does not detect this deadlock; the flushes wait until the lock timeout")
  void shouldNotDeadlockWhenTwoPartitionsAddTheSameNewVariableNamesInOppositeOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: new names a and d of one process definition
      final long processDefinition = nextKey(1);
      final String a = nextName("a");
      final String d = nextName("d");

      // when / then: P1 sees a, d, P2 sees d, a
      insertInOppositeOrder(kit, processDefinition, a, d);
    }
  }

  /**
   * The names exist already, e.g. after an exporter restart: the exporter's name cache is empty, so
   * it upserts every name again on its first sighting. The upserts find the rows and must not
   * conflict, also at REPEATABLE READ (MySQL/MariaDB), where {@code INSERT IGNORE} takes a shared
   * lock on the existing row.
   */
  @TestTemplate
  @FailsToday(
      on = H2,
      reason =
          "H2's lock timeout is shorter than the test holds the first thread (a test artefact)")
  void shouldNotDeadlockWhenTwoPartitionsUpsertExistingVariableNamesInOppositeOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: names a and d exist, recorded by a third partition's exporter
      final long processDefinition = nextKey(1);
      final String a = nextName("a");
      final String d = nextName("d");
      recordNames(kit, processDefinition, a, d);

      // when / then
      insertInOppositeOrder(kit, processDefinition, a, d);
    }
  }

  /** Name a exists, d is new: P1 sees a, d, P2 sees d, a. */
  @TestTemplate
  @FailsToday(
      on = ORACLE,
      reason =
          "Oracle's MERGE is not atomic: the second insert of the same new name fails with ORA-00001")
  @FailsToday(
      on = H2,
      reason =
          "H2's lock timeout is shorter than the test holds the first thread (a test artefact)")
  void shouldNotDeadlockWhenTwoPartitionsUpsertExistingAndNewVariableNamesInOppositeOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: name a exists, d is new
      final long processDefinition = nextKey(1);
      final String a = nextName("a");
      final String d = nextName("d");
      recordNames(kit, processDefinition, a);

      // when / then
      insertInOppositeOrder(kit, processDefinition, a, d);
    }
  }

  /**
   * Two partitions insert the same single new name at the same time; no opposite order involved.
   * The second insert waits for the first one's uncommitted row; once the first commits, the second
   * must skip the row, not fail.
   */
  @TestTemplate
  @FailsToday(
      on = ORACLE,
      reason =
          "Oracle's MERGE is not atomic: the second insert of the same new name fails with ORA-00001")
  @FailsToday(
      on = H2,
      reason =
          "H2's lock timeout is shorter than the test holds the first thread (a test artefact)")
  void shouldNotFailWhenTwoPartitionsAddTheSameNewVariableNameAtTheSameTime(
      final CamundaRdbmsTestApplication app) throws Exception {
    try (final var kit = new RdbmsConcurrencyKit(app)) {
      // given: a new name d of one process definition, seen by P1 and P2
      final long processDefinition = nextKey(1);
      final String d = nextName("d");
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      p1.export(variableCreated(1, nextKey(1), nextKey(1), processDefinition, d));
      p2.export(variableCreated(2, nextKey(2), nextKey(2), processDefinition, d));
      final var flush1 = kit.startFlush(p1);
      final var flush2 = kit.startFlush(p2);

      // when
      flush1.stepTo(INSERT_NAME);
      flush1.step(); // P1 inserts d, uncommitted
      flush2.stepTo(INSERT_NAME);
      flush2.step(); // P2 inserts d too: waits for P1's row
      flush1.release(); // P1 commits
      flush2.release(); // P2 must now skip d (Oracle: fails with ORA-00001)

      // then: both flushes committed; the second skipped the name instead of failing
      kit.assertNoErrors(flush1, flush2);
    }
  }

  /** Records the names through the exporter of partition 3, leaving partitions 1 and 2 unaware. */
  private static void recordNames(
      final RdbmsConcurrencyKit kit, final long processDefinition, final String... names) {
    final var p3 = kit.exporter(3);
    final long instance = nextKey(3);
    for (final String name : names) {
      p3.export(variableCreated(3, nextKey(3), instance, processDefinition, name));
    }
    p3.flush();
  }

  /**
   * P1 sees the names as first, second; P2 as second, first. Each upserts its first name and holds
   * it, then both upsert their second one, which the other one holds if it was new.
   */
  private static void insertInOppositeOrder(
      final RdbmsConcurrencyKit kit,
      final long processDefinition,
      final String first,
      final String second)
      throws InterruptedException {
    final var p1 = kit.exporter(1);
    final var p2 = kit.exporter(2);
    final long instance1 = nextKey(1);
    final long instance2 = nextKey(2);
    p1.export(
        variableCreated(1, nextKey(1), instance1, processDefinition, first),
        variableCreated(1, nextKey(1), instance1, processDefinition, second));
    p2.export(
        variableCreated(2, nextKey(2), instance2, processDefinition, second),
        variableCreated(2, nextKey(2), instance2, processDefinition, first));
    final var flush1 = kit.startFlush(p1);
    final var flush2 = kit.startFlush(p2);

    flush1.stepTo(INSERT_NAME); // P1 stops before upserting first
    flush2.stepTo(INSERT_NAME); // P2 stops before upserting second
    flush1.step(); // P1 upserts first
    flush2.step(); // P2 upserts second
    flush1.release(); // P1 upserts second: waits for P2 if second was new
    flush2.release(); // P2 upserts first: waits for P1 if first was new, a deadlock

    // no deadlock: both flushes committed, neither was chosen as the victim
    kit.assertNoErrors(flush1, flush2);
  }
}
