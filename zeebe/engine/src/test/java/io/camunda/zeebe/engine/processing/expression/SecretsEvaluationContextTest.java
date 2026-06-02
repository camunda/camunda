/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class SecretsEvaluationContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveSecretReferenceAsLiteralString() {
    // given
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRETS_1")
            .startEvent()
            .serviceTask("TASK", t -> t.zeebeJobTypeExpression("camunda.secrets.MY_TOKEN"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstance =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRETS_1").create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstance)
            .getFirst()
            .getValue();
    Assertions.assertThat(job).hasType("camunda.secrets.MY_TOKEN");
  }

  @Test
  public void shouldKeepSecretReferenceWhenConcatenatedWithStringLiterals() {
    // given
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRETS_2")
            .startEvent()
            .serviceTask(
                "TASK",
                t ->
                    t.zeebeJobTypeExpression("\"Bearer \" + camunda.secrets.AUTH_TOKEN + \"-end\""))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstance =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRETS_2").create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstance)
            .getFirst()
            .getValue();
    Assertions.assertThat(job).hasType("Bearer camunda.secrets.AUTH_TOKEN-end");
  }

  @Test
  public void shouldKeepSecretReferenceInsideObjectOutputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRETS_3")
            .startEvent()
            .serviceTask(
                "TASK_1",
                t ->
                    t.zeebeJobType("_1_")
                        .zeebeOutputExpression("{ token: camunda.secrets.API_KEY }", "credentials"))
            .serviceTask("TASK_2", t -> t.zeebeJobType("_2_"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstance =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRETS_3").create();

    // when
    ENGINE.job().ofInstance(processInstance).withType("_1_").complete();
    final var job = ENGINE.jobs().withType("_2_").activate().getValue().getJobs().getFirst();

    // then
    assertThat(job.getVariables())
        .containsEntry("credentials", Map.of("token", "camunda.secrets.API_KEY"));
  }

  @Test
  public void shouldNotInterfereWithUnrelatedCamundaSubNamespaces() {
    // given — camunda.foo is not registered; expression should yield null cleanly
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRETS_4")
            .startEvent()
            .serviceTask(
                "TASK_1",
                t ->
                    t.zeebeJobType("_1_")
                        .zeebeOutputExpression(
                            "if camunda.foo.bar = null then \"unset\" else \"set\"", "flag"))
            .serviceTask("TASK_2", t -> t.zeebeJobType("_2_"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstance =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRETS_4").create();

    // when
    ENGINE.job().ofInstance(processInstance).withType("_1_").complete();
    final var job = ENGINE.jobs().withType("_2_").activate().getValue().getJobs().getFirst();

    // then
    assertThat(job.getVariables()).containsEntry("flag", "unset");
  }
}
