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
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
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

  // ---- tolerated conditionals (#58614): detection records a reference for every branch at the
  // same pointer, but only the branch FEEL actually took ever puts a placeholder there - so the
  // untaken branch's reference must be ignored rather than fail the job closed.

  @Test
  public void shouldResolveTakenBranchOfConditionalOverTwoSecretsWhenConditionIsTrue() {
    // given - "if useProd then camunda.secrets.prodToken else camunda.secrets.devToken"
    final var process =
        Bpmn.createExecutableProcess("secret-conditional-branches-true")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-conditional-branches-true-job")
                        .zeebeInputExpression(
                            "if useProd then camunda.secrets.prodToken else camunda.secrets.devToken",
                            "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("secret-conditional-branches-true")
            .withVariable("useProd", true)
            .create();

    // then - the exported record keeps the taken branch's own placeholder (resolution only ever
    // reaches the activation response, never the persisted record - see the class-level Javadoc),
    // and no incident is raised for the untaken branch's reference
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("secret-conditional-branches-true-job")
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getVariables()).containsEntry("authToken", "camunda.secrets.prodToken");
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .incidentRecords()
                        .withIntent(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldResolveTakenBranchOfConditionalOverTwoSecretsWhenConditionIsFalse() {
    // given - the mirror of the above, so the outcome cannot depend on which reference is
    // materialized first
    final var process =
        Bpmn.createExecutableProcess("secret-conditional-branches-false")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-conditional-branches-false-job")
                        .zeebeInputExpression(
                            "if useProd then camunda.secrets.prodToken else camunda.secrets.devToken",
                            "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("secret-conditional-branches-false")
            .withVariable("useProd", false)
            .create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("secret-conditional-branches-false-job")
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getVariables()).containsEntry("authToken", "camunda.secrets.devToken");
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .incidentRecords()
                        .withIntent(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldResolveConditionalWhoseTakenBranchIsAPlainLiteral() {
    // given - "if true then \"localSecret\" else camunda.secrets.prod" always takes the
    // literal branch; the untaken branch's reference is still recorded at the same pointer
    final var process =
        Bpmn.createExecutableProcess("secret-conditional-literal")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-conditional-literal-job")
                        .zeebeInputExpression(
                            "if true then \"localSecret\" else camunda.secrets.prod", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("secret-conditional-literal").create();

    // then - the literal survives untouched, and no incident is raised for the untaken reference
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("secret-conditional-literal-job")
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getVariables()).containsEntry("authToken", "localSecret");
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .incidentRecords()
                        .withIntent(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldResolveConditionalWhoseTakenBranchIsAContextWithoutSecrets() {
    // given - "if true then {x: \"literal\"} else camunda.secrets.token" always takes the context
    // branch, which holds no secret reference; the untaken branch's reference is leaf-precise
    // (deploy-time validation permits this shape) and must leave the taken branch untouched
    final var process =
        Bpmn.createExecutableProcess("secret-conditional-context")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-conditional-context-job")
                        .zeebeInputExpression(
                            "if true then {x: \"literal\"} else camunda.secrets.token",
                            "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("secret-conditional-context").create();

    // then - the context literal survives untouched, and no incident is raised
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("secret-conditional-context-job")
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getVariables()).containsEntry("authToken", Map.of("x", "literal"));
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .incidentRecords()
                        .withIntent(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldResolveConditionalWhoseTakenBranchIsNullToNoValue() {
    // given - "if false then camunda.secrets.token else null" always takes the null branch;
    // detection still records the reference at the mapping target's pointer, but no
    // placeholder ever reaches it
    final var process =
        Bpmn.createExecutableProcess("secret-conditional-null")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("secret-conditional-null-job")
                        .zeebeInputExpression(
                            "if false then camunda.secrets.token else null", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("secret-conditional-null").create();

    // then - the variable is null, not a placeholder, and no incident is raised
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("secret-conditional-null-job")
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getVariables()).containsEntry("authToken", null);
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .incidentRecords()
                        .withIntent(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
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

  @Test
  public void shouldRejectStaticValueSecretReferenceInProperty() {
    // given - a static property value equal to a secret reference is a string literal
    final var process =
        Bpmn.createExecutableProcess("secret-property-static")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("job").zeebeProperty("authToken", "camunda.secrets.token"))
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
  public void shouldRejectFeelStringLiteralSecretReferenceInProperty() {
    // given - a FEEL string literal property value equal to a secret reference
    final var process =
        Bpmn.createExecutableProcess("secret-property-feel")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("job").zeebeProperty("authToken", "=\"camunda.secrets.token\""))
            .endEvent()
            .done();

    // when
    final var rejected = ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    assertThat(rejected.getRejectionReason())
        .contains("camunda.secrets.token")
        .contains("must be used as an expression");
  }

  /**
   * Regression for #59121: deploying a process whose input mapping embeds a multi-kilobyte escaped
   * JSON string must complete without a {@link StackOverflowError}. A greedy FEEL string-literal
   * regex overflowed the stream-processor stack on such payloads; the scanner now uses an unrolled
   * possessive pattern.
   */
  @Test
  public void shouldDeployProcessWithLongEscapedStringInInputMapping() {
    // given - FEEL object with a nested JSON-as-string full of \" escapes (no secret reference)
    final var escapedJson =
        "{\\\"type\\\":\\\"AdaptiveCard\\\",\\\"body\\\":" + "\\\"x\\\"".repeat(2000) + "}";
    final var process =
        Bpmn.createExecutableProcess("long-escaped-input")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("long-escaped-job")
                        .zeebeInputExpression(
                            "{\"content\": \"" + escapedJson + "\"}", "data.attachments"))
            .endEvent()
            .done();

    // when / then - deployment must succeed; a StackOverflowError would kill the engine thread
    final var deployment = ENGINE.deployment().withXmlResource(process).deploy();
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
    assertThat(deployment.getValue().getProcessesMetadata()).hasSize(1);
  }

  /**
   * Companion to {@link #shouldDeployProcessWithLongEscapedStringInInputMapping}: the same long
   * escaped payload must still reject a secret reference embedded as a quoted literal, without
   * crashing the broker.
   */
  @Test
  public void shouldRejectSecretReferenceInsideLongEscapedStringInInputMapping() {
    // given
    final var escapedJson =
        "{\\\"auth\\\":\\\"camunda.secrets.token\\\",\\\"pad\\\":" + "\\\"x\\\"".repeat(2000) + "}";
    final var process =
        Bpmn.createExecutableProcess("long-escaped-secret-literal")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("job")
                        .zeebeInputExpression(
                            "{\"content\": \"" + escapedJson + "\"}", "data.attachments"))
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
  public void shouldRejectSecretReferenceInsideListLiteralInInputMapping() {
    // given - a secret reference inside a FEEL list literal is recorded at the enclosing path,
    // which can never resolve to a text leaf at injection (#58614)
    final var process =
        Bpmn.createExecutableProcess("secret-list")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("job").zeebeInputExpression("[camunda.secrets.token]", "creds"))
            .endEvent()
            .done();

    // when
    final var rejected = ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    assertThat(rejected.getRejectionReason())
        .contains("camunda.secrets.token")
        .contains("would never be filled in");
  }

  @Test
  public void shouldRejectSecretReferenceInsideContextProducedByAnIfBranchInInputMapping() {
    // given - a reference inside a context produced by a branch of the expression is recorded
    // at the enclosing path too (#58614)
    final var process =
        Bpmn.createExecutableProcess("secret-if-context")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("job")
                        .zeebeInputExpression(
                            "if true then {x: camunda.secrets.token} else null", "creds"))
            .endEvent()
            .done();

    // when
    final var rejected = ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    assertThat(rejected.getRejectionReason())
        .contains("camunda.secrets.token")
        .contains("would never be filled in");
  }
}
