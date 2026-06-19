/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that {@link Capability#JOB_PRIORITIZATION} gates the *job-creation* path of issue
 * #50567, not just the priority-update API.
 *
 * <p>Two write-side effects of the feature must stay inactive under low ECV:
 *
 * <ul>
 *   <li>{@code BpmnJobBehavior} skips the BPMN-declared priority expression and writes priority=0
 *       on the JobRecord — so the v=2 record stream is byte-identical to a pre-PR broker.
 *   <li>{@code selectVersionFor(JobIntent.CREATED)} returns v=2 (legacy path) instead of v=3 (the
 *       priority-CF path). {@code JobCreatedV2Applier} routes the job to the legacy {@code
 *       JOB_ACTIVATABLE} column family; the priority CF is untouched.
 * </ul>
 *
 * <p>This test pins the negative side (gate closed). The full-feature happy path is covered by
 * {@code JobPriorityTest}, {@code JobActivationByPriorityTest}, and {@code
 * JobPriorityStreamingActivationTest}, all of which now opt in via {@code
 * withInitialClusterVersionAtMax()}.
 */
public final class JobPriorityCreationGateTest {

  private static final String PROCESS_ID = "process";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  // intentionally NOT calling withInitialClusterVersionAtMax() — the gate stays closed

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitJobAtV2WithPriorityZeroWhenCapabilityInactive() {
    // given — BPMN declares a service-task priority of 50, but ECV is at BASELINE
    final String jobType = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType).zeebeJobPriority("50"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — the JOB.CREATED record reaches the log with priority=0 (BpmnJobBehavior gate
    // suppressed the expression eval) and recordVersion=2 (selectVersionFor returns v=2 because
    // v=3 is gated above the active ordinal). State is byte-identical to a pre-PR broker.
    final var created =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst();
    assertThat(created.getRecordVersion()).as("v=3 is gated → write side selects v=2").isEqualTo(2);
    assertThat(created.getValue().getPriority())
        .as("BpmnJobBehavior forced priority to 0 under low ECV")
        .isZero();
  }
}
