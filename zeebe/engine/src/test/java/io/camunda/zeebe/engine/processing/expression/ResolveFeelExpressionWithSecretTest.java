/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.secret.SecretStore;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Standalone FEEL evaluation endpoint behaviour for {@code camunda.secret.*} references.
 * Materialises the underlying secret value from the configured {@link SecretStore} on this path
 * only — every other engine-side FEEL evaluation continues to see literal reference strings.
 */
public class ResolveFeelExpressionWithSecretTest {

  private static final Map<String, String> SECRETS =
      Map.of(
          "SLACK_BOT_TOKEN", "xoxb-real-token",
          "STRIPE_API_KEY", "sk_test_real");

  // In-memory SecretStore for the test JVM. Bound to a class-level constant so the EngineRule
  // sees a deterministic backend regardless of which env vars happen to be set on the host.
  private static final SecretStore TEST_STORE = name -> Optional.ofNullable(SECRETS.get(name));

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().withSecretStore(TEST_STORE);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveSecretToRealValue() {
    // when
    final var record =
        ENGINE.expression().withExpression("=camunda.secret.SLACK_BOT_TOKEN").resolve();

    // then — the synchronous endpoint response carries the underlying secret value, not the
    // literal reference string that the engine-wide context would produce.
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("xoxb-real-token");
  }

  @Test
  public void shouldResolveCompoundExpressionWithSecret() {
    // when — the canonical "Bearer <secret>" connector idiom evaluated at the endpoint
    final var record =
        ENGINE
            .expression()
            .withExpression("=\"Bearer \" + camunda.secret.STRIPE_API_KEY")
            .resolve();

    // then — concatenation runs against the resolved value, not the reference string
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("Bearer sk_test_real");
  }

  @Test
  public void shouldReturnNullForMissingSecret() {
    // when
    final var record =
        ENGINE.expression().withExpression("=camunda.secret.DOES_NOT_EXIST").resolve();

    // then — undefined path resolves to FEEL null with a warning surfaced on the record, matching
    // how cluster-variable misses are reported by the same endpoint
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isNull();
    assertThat(record.getValue().getWarnings()).isNotEmpty();
  }

  @Test
  public void shouldNotMaterializeSecretInsideEngineEvenWhenStoreIsConfigured() {
    // given — same secret store as the endpoint, but evaluation runs through an input mapping
    // (engine-wide FEEL path), which must keep using the literal-reference context.
    final var process =
        Bpmn.createExecutableProcess("PROCESS_ENGINE_BOUNDARY")
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
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ENGINE_BOUNDARY").create();

    // then — the variable still carries the harmless reference string; the resolving context
    // configured for the endpoint never bleeds into the variable-store path.
    final var tokenVar =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("token")
            .getFirst();

    assertThat(tokenVar.getValue().getValue()).isEqualTo("\"camunda.secret.SLACK_BOT_TOKEN\"");
  }
}
