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
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.nextName;
import static io.camunda.it.rdbms.concurrency.kit.KitRecords.withNextPosition;
import static io.camunda.it.rdbms.concurrency.kit.SyncPoint.statement;

import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.it.rdbms.concurrency.kit.FailsToday;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyKit;
import io.camunda.it.rdbms.concurrency.kit.RdbmsConcurrencyTest;
import io.camunda.it.rdbms.concurrency.kit.SyncPoint;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.TestTemplate;

/**
 * <b>Situation.</b> The variable name lookup deadlock of {@link VariableNameLookupConcurrencyIT},
 * with the records of a real engine instead of hand-built ones. It answers the question whether the
 * engine emits the variables of a document in a fixed order (then the deadlock could not happen):
 * it does not. The engine iterates a hash map over the names' byte offsets in the document, so the
 * order of the VARIABLE records depends on the sizes of the values.
 *
 * <p><b>How.</b> A broker with two partitions starts 20 instances of one process, all with the same
 * two variables a and d, with values of varying size. The test picks an instance on partition 1
 * whose records come as a, d and one on partition 2 whose records come as d, a, and replays those
 * records unchanged (with new positions) into the kit's exporters of partitions 1 and 2. The
 * interleaving is then the one of {@link VariableNameLookupConcurrencyIT}.
 *
 * <p><b>Today.</b> Deadlock on every database (H2 waits until its lock timeout): the order is the
 * engine's, not the test's.
 */
@RdbmsConcurrencyTest
@ZeebeIntegration
class VariableNameOrderFromEngineConcurrencyIT {

  private static final SyncPoint.Match INSERT_NAME =
      statement("VariableMapper.insertLookupIfNotExists");

  @TestZeebe(partitionCount = 2)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withSecondaryStorageType(SecondaryStorageType.none)
          .withUnifiedConfig(c -> c.getCluster().setPartitionCount(2));

  @TestTemplate
  @FailsToday(
      on = {POSTGRES, MYSQL, MARIADB, ORACLE, MSSQL},
      reason = "each flush holds the new name it inserted first and needs the other one: deadlock")
  @FailsToday(
      on = H2,
      reason = "H2 does not detect this deadlock; the flushes wait until the lock timeout")
  void shouldNotDeadlockWhenTheEngineEmitsTheSameNewVariableNamesInOppositeOrder(
      final CamundaRdbmsTestApplication app) throws Exception {
    // given: the engine's records of an instance on partition 1 with a before d, and of one on
    // partition 2 with d before a
    final String a = nextName("a");
    final String d = nextName("d");
    final var instances = startInstancesWithVariables(a, d);
    final var onPartition1 = firstWithOrder(instances, 1, a, d);
    final var onPartition2 = firstWithOrder(instances, 2, d, a);
    if (onPartition1 == null || onPartition2 == null) {
      throw new IllegalStateException(
          "no instances with opposite variable order on partitions 1 and 2: " + orders(instances));
    }

    try (final var kit = new RdbmsConcurrencyKit(app)) {
      final var p1 = kit.exporter(1);
      final var p2 = kit.exporter(2);
      onPartition1.forEach(r -> p1.export(withNextPosition(r)));
      onPartition2.forEach(r -> p2.export(withNextPosition(r)));
      final var flush1 = kit.startFlush(p1);
      final var flush2 = kit.startFlush(p2);

      // when
      flush1.stepTo(INSERT_NAME); // P1 stops before inserting a
      flush2.stepTo(INSERT_NAME); // P2 stops before inserting d
      flush1.step(); // P1 inserts a and holds it
      flush2.step(); // P2 inserts d and holds it
      flush1.release(); // P1 inserts d: waits for P2
      flush2.release(); // P2 inserts a: waits for P1, a deadlock

      // then: no deadlock: both flushes committed, neither was chosen as the victim
      kit.assertNoErrors(flush1, flush2);
    }
  }

  /**
   * Deploys a process and starts 20 instances with variables a and d, whose value sizes vary, so
   * the engine emits them in different orders.
   *
   * @return per instance, its two VARIABLE CREATED records in the order the engine wrote them
   */
  private List<List<Record<VariableRecordValue>>> startInstancesWithVariables(
      final String a, final String d) {
    final String processId = nextName("process");
    final List<List<Record<VariableRecordValue>>> instances = new ArrayList<>();
    try (final var client = broker.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
              processId + ".bpmn")
          .send()
          .join();
      for (int size = 0; size < 20; size++) {
        final Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(a, "x".repeat(size * 7));
        variables.put(d, "y".repeat((20 - size) * 5));
        final long instance =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(processId)
                .latestVersion()
                .variables(variables)
                .send()
                .join()
                .getProcessInstanceKey();
        instances.add(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(instance)
                .limit(2)
                .asList());
      }
    }
    return instances;
  }

  private static List<Record<VariableRecordValue>> firstWithOrder(
      final List<List<Record<VariableRecordValue>>> instances,
      final int partition,
      final String first,
      final String second) {
    return instances.stream()
        .filter(records -> Protocol.decodePartitionId(records.getFirst().getKey()) == partition)
        .filter(records -> names(records).equals(List.of(first, second)))
        .findFirst()
        .orElse(null);
  }

  private static List<String> names(final List<Record<VariableRecordValue>> records) {
    return records.stream().map(r -> r.getValue().getName()).toList();
  }

  /** Per instance: partition and the order of its variable names, for the failure message. */
  private static List<String> orders(final List<List<Record<VariableRecordValue>>> instances) {
    return instances.stream()
        .map(
            records ->
                "P"
                    + Protocol.decodePartitionId(records.getFirst().getKey())
                    + " "
                    + names(records))
        .toList();
  }
}
