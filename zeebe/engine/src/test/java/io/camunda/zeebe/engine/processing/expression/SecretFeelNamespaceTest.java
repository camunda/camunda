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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * End-to-end coverage for the engine-wide {@code camunda.secret.*} FEEL namespace. The PoC's rule
 * is that secret references evaluated outside the two materialising paths (job activation via
 * {@code fetchVariables} and the standalone FEEL evaluation endpoint) yield the literal reference
 * string instead of the underlying secret value. This guarantees real secrets never land in the
 * variable store, regardless of how the BPMN author wires them.
 */
public class SecretFeelNamespaceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void inputMappingShouldPersistLiteralReferenceString() {
    // given — a service task whose input mapping binds a variable directly to a secret reference
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRET_INPUT")
            .startEvent()
            .serviceTask(
                "TASK",
                t ->
                    t.zeebeJobType("noop")
                        .zeebeInputExpression("camunda.secret.SLACK_BOT_TOKEN", "token"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRET_INPUT").create();

    // then — the persisted variable is the harmless reference string, not the real secret
    final Record<VariableRecordValue> tokenVar =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("token")
            .getFirst();

    assertThat(tokenVar.getValue().getValue()).isEqualTo("\"camunda.secret.SLACK_BOT_TOKEN\"");
  }

  @Test
  public void compoundExpressionShouldKeepReferenceSubstringIntact() {
    // given — a common connector idiom: build an Authorization header from a secret reference
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRET_COMPOUND")
            .startEvent()
            .serviceTask(
                "TASK",
                t ->
                    t.zeebeJobType("noop")
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secret.STRIPE_API_KEY", "authHeader"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRET_COMPOUND").create();

    // then — concatenation leaves the reference substring intact, ready for late substitution
    // when the worker explicitly fetches the secret in a later stage.
    final Record<VariableRecordValue> authVar =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("authHeader")
            .getFirst();

    assertThat(authVar.getValue().getValue()).isEqualTo("\"Bearer camunda.secret.STRIPE_API_KEY\"");
  }

  @Test
  public void jobTypeExpressionShouldResolveToReferenceNotSecretValue() {
    // given — a defensive case: someone writes the job type as a secret reference. The worker
    // must NOT see the real secret value as the job type (and the job-type field is exported).
    final var process =
        Bpmn.createExecutableProcess("PROCESS_SECRET_JOB_TYPE")
            .startEvent()
            .serviceTask("TASK", t -> t.zeebeJobTypeExpression("camunda.secret.SOME_NAME"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_SECRET_JOB_TYPE").create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(job).hasType("camunda.secret.SOME_NAME");
  }
}
