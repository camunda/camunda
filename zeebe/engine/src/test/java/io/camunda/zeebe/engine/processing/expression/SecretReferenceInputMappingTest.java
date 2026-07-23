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
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * End-to-end coverage for handling {@code camunda.secrets.<name>} in FEEL input-mapping evaluation
 * (issue #57178): an expression reference resolves to its own string-literal placeholder for input
 * mappings only, cluster variables are not shadowed, and a literal reference is rejected at deploy.
 */
public final class SecretReferenceInputMappingTest {

  /**
   * All referenced secrets resolve to a cached value, otherwise activation would remove the jobs
   * from the batch. The activated records asserted on below always keep the placeholders: resolved
   * values are only injected into the activation response, never the persisted records.
   */
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(SecretStoreRegistries.resolveAll("resolved"));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveSecretReferenceInInputMappingToItsPlaceholder() {
    // given
    final var process =
        Bpmn.createExecutableProcess("secret-input")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-input-job")
                        .zeebeInputExpression("camunda.secrets.externalSystemToken", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("secret-input").create();

    // then
    final JobRecordValue job =
        ENGINE.jobs().withType("secret-input-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getVariables())
        .containsEntry("authToken", "camunda.secrets.externalSystemToken");
  }

  @Test
  public void shouldResolveSecretReferenceInsideConcatenationInInputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess("secret-concat")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-concat-job")
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets.token", "authorization"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("secret-concat").create();

    // then
    final JobRecordValue job =
        ENGINE.jobs().withType("secret-concat-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("authorization", "Bearer camunda.secrets.token");
  }

  @Test
  public void shouldNotResolveSecretReferenceInOutputMapping() {
    // given - the secret reference is used in an OUTPUT mapping, which must be left untouched
    final var process =
        Bpmn.createExecutableProcess("secret-output")
            .startEvent()
            .serviceTask(
                "producer",
                t ->
                    t.zeebeJobType("secret-output-job")
                        .zeebeOutputExpression("camunda.secrets.token", "result"))
            .serviceTask("consumer", t -> t.zeebeJobType("consumer-job"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("secret-output").create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("secret-output-job").complete();

    // then - output mapping did not resolve the reference; the variable is null, not a placeholder
    final JobRecordValue job =
        ENGINE.jobs().withType("consumer-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("result", null);
  }

  @Test
  public void shouldResolveClusterVariableAndSecretReferenceInSameInputMapping() {
    // given
    ENGINE.clusterVariables().withName("REGION").withValue("\"eu-1\"").setGlobalScope().create();

    final var process =
        Bpmn.createExecutableProcess("secret-and-cluster")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-and-cluster-job")
                        .zeebeInputExpression("camunda.vars.cluster.REGION", "region")
                        .zeebeInputExpression("camunda.secrets.token", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("secret-and-cluster").create();

    // then - cluster variable resolves to its value, secret reference to its placeholder
    final JobRecordValue job =
        ENGINE.jobs().withType("secret-and-cluster-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables())
        .containsEntry("region", "eu-1")
        .containsEntry("authToken", "camunda.secrets.token");
  }

  @Test
  public void shouldRejectStaticValueSecretReferenceInInputMapping() {
    // given - a static value (no leading '=') equal to a secret reference is a string literal
    final var process =
        Bpmn.createExecutableProcess("secret-literal-static")
            .startEvent()
            .serviceTask(
                "task", t -> t.zeebeJobType("job").zeebeInput("camunda.secrets.token", "authToken"))
            .endEvent()
            .done();

    // when
    final var rejected = ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    assertThat(rejected.getRejectionReason())
        .contains("camunda.secrets.token")
        .contains("must be used as an expression");
  }

  @Test
  public void shouldRejectFeelStringLiteralSecretReferenceInInputMapping() {
    // given - a FEEL string literal equal to a secret reference
    final var process =
        Bpmn.createExecutableProcess("secret-literal-feel")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("job").zeebeInput("=\"camunda.secrets.token\"", "authToken"))
            .endEvent()
            .done();

    // when
    final var rejected = ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    assertThat(rejected.getRejectionReason())
        .contains("camunda.secrets.token")
        .contains("must be used as an expression");
  }
}
