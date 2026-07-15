/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies a general job-activation contract: a job created before a broker restart must remain
 * activatable afterwards, once the broker rebuilds its state by replaying the persisted event log.
 *
 * <p>This restarts the same broker binary, so no {@code recordVersion} boundary is crossed — it
 * does not exercise cross-version replay dispatch and, on its own, does not verify the #56962 fix
 * (see {@code JobActivatableReplayVersionDispatchTest} and {@code
 * JobActivatableReplayDivergenceTest} in {@code zeebe-engine} for that). It still guards a real
 * user-facing behavior: a job must not be lost, or become permanently stuck non-activatable, across
 * a restart.
 */
@MultiDbTest
// ScriptBasedSchemaManager reruns its DDL (no IF NOT EXISTS) on every Spring context refresh, and
// the in-place broker restart below creates a new context while the RDBMS store persists across
// it, so schema init fails with "table already exists". No other test restarts a broker in place
// under @MultiDbTest, so this gap was never hit before. ES/OS are unaffected since index creation
// there is naturally idempotent.
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
public class JobActivationSurvivesRestartIT {

  @TempDir private static Path workingDirectory;

  @MultiDbTestApplication(managedLifecycle = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withUnauthenticatedAccess().withRecordingExporter(true);

  @BeforeAll
  static void setUp() {
    BROKER.withWorkingDirectory(workingDirectory);
    BROKER.start();
    BROKER.awaitCompleteTopology();
  }

  @AfterAll
  static void tearDown() {
    BROKER.stop();
  }

  @Test
  void shouldActivateJobCreatedBeforeRestartAfterBrokerReplaysItsLog() {
    // given
    final String jobType = "replay-activation-job";
    final String processId = "replay-activation-process";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    try (final CamundaClient client = BROKER.newClientBuilder().build()) {
      client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }

    // the job must be durably logged as activatable before the restart — otherwise the restart
    // would race the job's creation instead of testing replay of it
    assertThat(RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).limit(1))
        .as("job is created before the restart")
        .hasSize(1);

    // when - restart the broker, forcing it to rebuild its job-activation state by replaying the
    // persisted event log
    BROKER.stop();
    BROKER.start();
    BROKER.awaitCompleteTopology();

    // then - the job created before the restart is still activatable afterwards
    try (final CamundaClient client = BROKER.newClientBuilder().build()) {
      final List<ActivatedJob> activatedJobs = new ArrayList<>();
      Awaitility.await("job should be activatable after the broker replays its log")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                activatedJobs.clear();
                activatedJobs.addAll(
                    client
                        .newActivateJobsCommand()
                        .jobType(jobType)
                        .maxJobsToActivate(1)
                        .send()
                        .join()
                        .getJobs());
                assertThat(activatedJobs).hasSize(1);
              });

      assertThat(activatedJobs.getFirst().getType()).isEqualTo(jobType);
    }
  }
}
