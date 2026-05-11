/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.processing.secret.SecretStore;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Job activation behaviour for the {@code camunda.secret.*} FEEL namespace. The broker resolves a
 * secret value only when the worker explicitly lists the full reference name in {@code
 * fetchVariables} — every other path keeps the literal reference string from the variable store.
 */
public class ActivateJobsWithSecretsTest {

  private static final Map<String, String> SECRETS = Map.of("SLACK_BOT_TOKEN", "xoxb-real-token");

  private static final SecretStore TEST_STORE = name -> Optional.ofNullable(SECRETS.get(name));

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().withSecretStore(TEST_STORE);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldMaterializeSecretWhenWorkerExplicitlyFetchesIt() {
    // given — service task with no special configuration; the secret reference will be added
    // by the worker's fetchVariables list, not by the BPMN model.
    final String jobType = "secret-job-A";
    final var process =
        Bpmn.createExecutableProcess("PROCESS_FETCH_SECRET")
            .startEvent()
            .serviceTask("TASK", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    ENGINE.processInstance().ofBpmnProcessId("PROCESS_FETCH_SECRET").create();

    // when — worker pulls one job and asks for the secret by its full reference name
    final var batch =
        ENGINE
            .jobs()
            .withType(jobType)
            .withFetchVariables("camunda.secret.SLACK_BOT_TOKEN")
            .withMaxJobsToActivate(1)
            .activate();

    // then — the activation response carries the real secret value at the same key the worker
    // asked for. The variable store itself was never touched.
    assertThat(batch.getIntent()).isEqualTo(JobBatchIntent.ACTIVATED);
    assertThat(batch.getValue().getJobs()).hasSize(1);
    assertThat(batch.getValue().getJobs().getFirst().getVariables())
        .containsExactly(entry("camunda.secret.SLACK_BOT_TOKEN", "xoxb-real-token"));
  }

  @Test
  public void shouldNotMaterializeSecretsWhenWorkerDoesNotFetchThem() {
    // given — an input mapping persists a reference string into the variable store. The worker
    // does NOT list it in fetchVariables, so no resolution should happen.
    final String jobType = "secret-job-B";
    final var process =
        Bpmn.createExecutableProcess("PROCESS_NO_FETCH")
            .startEvent()
            .serviceTask(
                "TASK",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("camunda.secret.SLACK_BOT_TOKEN", "token"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    ENGINE.processInstance().ofBpmnProcessId("PROCESS_NO_FETCH").create();

    // when — worker fetches the regular variable that holds the literal reference
    final var batch =
        ENGINE
            .jobs()
            .withType(jobType)
            .withFetchVariables("token")
            .withMaxJobsToActivate(1)
            .activate();

    // then — variable comes through as the literal reference string; no env-var lookup happened
    assertThat(batch.getIntent()).isEqualTo(JobBatchIntent.ACTIVATED);
    assertThat(batch.getValue().getJobs()).hasSize(1);
    assertThat(batch.getValue().getJobs().getFirst().getVariables())
        .containsExactly(entry("token", "camunda.secret.SLACK_BOT_TOKEN"));
  }

  @Test
  public void shouldRaiseIncidentForMissingSecret() {
    // given — two distinct task types so we can show that the missing-secret failure is
    // per-job and does not block the rest of the batch.
    final String missingType = "secret-job-missing";
    final var process =
        Bpmn.createExecutableProcess("PROCESS_MISSING_SECRET")
            .startEvent()
            .serviceTask("TASK", t -> t.zeebeJobType(missingType))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_MISSING_SECRET").create();

    // when — worker requests a secret that is not in the test store
    final var batch =
        ENGINE
            .jobs()
            .withType(missingType)
            .withFetchVariables("camunda.secret.DOES_NOT_EXIST")
            .withMaxJobsToActivate(1)
            .activate();

    // then — the missing-secret job is not in the activation response, and an incident is
    // raised against it with the SECRET_NOT_FOUND error type and the offending reference name in
    // the message.
    assertThat(batch.getValue().getJobs()).isEmpty();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    assertThat(incident.getErrorType()).isEqualTo(ErrorType.SECRET_NOT_FOUND);
    assertThat(incident.getErrorMessage()).contains("camunda.secret.DOES_NOT_EXIST");
    assertThat(incident.getJobKey()).isPositive();
  }
}
