/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
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
  void shouldReportDefinitionDrainingButGoneFromDeploymentPartition() {
    // given - a partitions dir where the definition is gone from P1 but still DRAINING on P2
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, processState -> {});
    seedPartition(partitions, 2, processState -> markDrainingDefinition(processState));

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isEqualTo(2);
    assertThat(out.toString())
        .contains("key=" + PROCESS_DEFINITION_KEY)
        .contains("bpmnProcessId=" + BPMN_PROCESS_ID)
        .contains("draining=[2]")
        .contains("absent=[1]")
        .contains("stranded=1");
  }

  @Test
  void shouldNotReportDefinitionStillPresentOnDeploymentPartition() {
    // given - the definition is DRAINING on both partitions: a normal in-progress drain
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 1, processState -> markDrainingDefinition(processState));
    seedPartition(partitions, 2, processState -> markDrainingDefinition(processState));

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isZero();
    assertThat(out.toString().trim()).endsWith("stranded=0 partitions=[1, 2]");
  }

  @Test
  void shouldFailWhenDeploymentPartitionMissing() {
    // given - a partitions dir with no subdirectory for the deployment partition (id 1)
    final Path partitions = tempDir.resolve("partitions");
    seedPartition(partitions, 2, processState -> markDrainingDefinition(processState));

    // when
    final int exitCode = run(partitions);

    // then
    assertThat(exitCode).isOne();
    assertThat(err.toString()).contains("deployment partition");
  }

  private int run(final Path partitions) {
    return commandLine.execute(
        "check-process-definition-deletions",
        "-r",
        partitions.toString(),
        "--runtime=" + tempDir.resolve("runtime"));
  }

  private void seedPartition(
      final Path partitionsRoot, final int partitionId, final Consumer<DbProcessState> seed) {
    final Path partitionDir = partitionsRoot.resolve(String.valueOf(partitionId));
    try (final var runtime =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("seed-" + partitionId).toFile())) {
      final var context = runtime.createContext();
      final var processState =
          new DbProcessState(runtime, context, new EngineConfiguration(), transformer());
      context.runInTransaction(() -> seed.accept(processState));
      new SnapshotUtil().takeSnapshot(runtime, partitionDir, "1-1-1-1-1", 1L);
    }
  }

  private void markDrainingDefinition(final DbProcessState processState) {
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

  private static BpmnTransformer transformer() {
    return BpmnFactory.createTransformer(
        InstantSource.fixed(Instant.EPOCH), ExpressionLanguageMetrics.noop(), Integer.MAX_VALUE);
  }
}
