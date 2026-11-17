/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StateCheckProcessDefinitionDeletionsCommandTest {

  private static final long PROCESS_DEFINITION_KEY = 2251799813685249L;
  private static final String BPMN_PROCESS_ID = "order-process";

  @TempDir Path tempDir;
  CommandLine commandLine;
  StringWriter err;
  StringWriter out;

  @BeforeEach
  public void setup() {
    err = new StringWriter();
    out = new StringWriter();
    // Root at StateCommand directly so picocli does not eagerly load the sibling
    // TopologyMetaCommand
    // (and its protobuf dependencies) that live under the top-level Main command.
    commandLine =
        new CommandLine(new StateCommand())
            .setErr(new PrintWriter(err))
            .setOut(new PrintWriter(out));
  }

  @Test
  void shouldReportDefinitionDrainingWithoutCoordination() {
    // given - the definition is DRAINING on P2 but the deployment partition tracks no pending
    // deletion for it: the deletion can never finish
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, seeding -> seeding.initializeRouting(2));
    seedPartition(partitions, 2, seeding -> seeding.markDraining());

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isEqualTo(2);
    assertThat(out.toString())
        .contains("key=" + PROCESS_DEFINITION_KEY)
        .contains("bpmnProcessId=" + BPMN_PROCESS_ID)
        .contains("draining=[2]")
        .contains("uncoordinated=[2]")
        .contains("orphanedInstances=false")
        .contains("stranded=1");
  }

  @Test
  void shouldNotReportHealthyInProgressDrain() {
    // given - the definition is DRAINING on both partitions and the deployment partition still
    // holds
    // a pending-deletion entry for each: a normal in-progress drain
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(
        partitions,
        1,
        seeding -> {
          seeding.initializeRouting(2);
          seeding.markDraining();
          seeding.addPendingDeletion(1);
          seeding.addPendingDeletion(2);
        });
    seedPartition(partitions, 2, seeding -> seeding.markDraining());

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isZero();
    assertThat(out.toString().trim()).endsWith("stranded=0 partitions=[1, 2]");
  }

  @Test
  void shouldReportOrphanedInstancesWhenTheyRemain() {
    // given - a stuck definition on P2 that still has an active process instance
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, seeding -> seeding.initializeRouting(2));
    seedPartition(
        partitions,
        2,
        seeding -> {
          seeding.markDraining();
          seeding.addActiveInstance(2251799813685260L);
        });

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isEqualTo(2);
    assertThat(out.toString()).contains("orphanedInstances=true");
  }

  @Test
  void shouldFailWhenDeploymentPartitionMissing() {
    // given - a partitions dir with no subdirectory for the deployment partition (id 1)
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 2, seeding -> seeding.markDraining());

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isOne();
    assertThat(err.toString()).contains("deployment partition");
  }

  @Test
  void shouldScanUsingRoutingStateWhenAllPartitionsPresent() {
    // given - routing state lists partitions 1 and 2, both provided; the definition is stuck on P2
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, seeding -> seeding.initializeRouting(2));
    seedPartition(partitions, 2, seeding -> seeding.markDraining());

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isEqualTo(2);
    assertThat(out.toString()).contains("draining=[2]").contains("uncoordinated=[2]");
  }

  @Test
  void shouldFailWhenRoutingStateEmpty() {
    // given - the deployment partition snapshot carries no routing state, so the authoritative
    // partition set is unknown
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, seeding -> {});
    seedPartition(partitions, 2, seeding -> seeding.markDraining());

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isOne();
    assertThat(err.toString()).contains("routing state is empty");
  }

  @Test
  void shouldFailWhenAnExpectedPartitionIsMissing() {
    // given - routing state says the cluster has partitions 1..3, but only 1 and 2 are provided
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, seeding -> seeding.initializeRouting(3));
    seedPartition(partitions, 2, seeding -> seeding.markDraining());

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isOne();
    assertThat(err.toString()).contains("incomplete").contains("[3]");
  }

  @Test
  void shouldFailWhenAPartitionHasNoSnapshot() throws Exception {
    // given - partition 2's directory exists but holds no snapshot
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, seeding -> seeding.initializeRouting(2));
    Files.createDirectories(partitions.resolve("2"));

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isOne();
    assertThat(err.toString())
        .contains("incomplete")
        .contains("without a snapshot")
        .contains("[2]");
  }

  private int run(final Path partitions) {
    return commandLine.execute(
        "check-process-definition-deletions",
        "-r",
        partitions.toString(),
        "--runtime=" + tempDir.resolve("runtime"));
  }

  private void seedPartition(
      final Path partitionsRoot, final int partitionId, final Consumer<Seeding> seed) {
    final Path partitionDir = partitionsRoot.resolve(String.valueOf(partitionId));
    try (final var runtime =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("seed-" + partitionId).toFile())) {
      final var context = runtime.createContext();
      final var seeding = new Seeding(runtime, context);
      context.runInTransaction(() -> seed.accept(seeding));
      new SnapshotUtil().takeSnapshot(runtime, partitionDir, "1-1-1-1-1", 1L);
    }
  }

  private static BpmnTransformer transformer() {
    return BpmnFactory.createTransformer(
        InstantSource.fixed(Instant.EPOCH), ExpressionLanguageMetrics.noop(), Integer.MAX_VALUE);
  }

  /** Test helper that writes the various column families the command reads. */
  private static final class Seeding {
    private final DbProcessState processState;
    private final DbRoutingState routingState;
    private final DbLong definitionKey = new DbLong();
    private final DbLong instanceKey = new DbLong();
    private final io.camunda.zeebe.db.ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
        instancesByDefinition;

    Seeding(final ZeebeDb<ZbColumnFamilies> db, final TransactionContext context) {
      processState = new DbProcessState(db, context, new EngineConfiguration(), transformer());
      routingState = new DbRoutingState(db, context);
      instancesByDefinition =
          db.createColumnFamily(
              ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY,
              context,
              new DbCompositeKey<>(definitionKey, instanceKey),
              DbNil.INSTANCE);
    }

    void markDraining() {
      final var resource =
          Bpmn.convertToString(
              Bpmn.createExecutableProcess(BPMN_PROCESS_ID).startEvent().endEvent().done());
      final var record =
          new ProcessRecord()
              .setKey(PROCESS_DEFINITION_KEY)
              .setBpmnProcessId(BPMN_PROCESS_ID)
              .setVersion(3)
              .setResourceName(BPMN_PROCESS_ID + ".bpmn")
              .setResource(BufferUtil.wrapString(resource))
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
              .setDeleteHistory(true);
      processState.putProcess(PROCESS_DEFINITION_KEY, record);
      processState.markDraining(record);
    }

    void addPendingDeletion(final int partitionId) {
      processState.addPendingDeletion(PROCESS_DEFINITION_KEY, partitionId);
    }

    void addActiveInstance(final long processInstanceKey) {
      definitionKey.wrapLong(PROCESS_DEFINITION_KEY);
      instanceKey.wrapLong(processInstanceKey);
      instancesByDefinition.insert(
          new DbCompositeKey<>(definitionKey, instanceKey), DbNil.INSTANCE);
    }

    void initializeRouting(final int partitionCount) {
      routingState.initializeRoutingInfo(partitionCount);
    }
  }
}
