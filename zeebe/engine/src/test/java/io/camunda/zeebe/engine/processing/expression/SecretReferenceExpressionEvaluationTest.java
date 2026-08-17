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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * End-to-end coverage for handling {@code camunda.secrets.<name>} in the expression evaluation
 * endpoint ({@code POST /v2/expression/evaluation}).
 *
 * <p>Inbound connectors have no job, so job-activation secret resolution never runs for them; they
 * resolve FEEL through this endpoint instead. A secret reference must therefore evaluate to its own
 * string-literal placeholder (never {@code null} and never the resolved value), mirroring the
 * input-mapping behavior. No secret store is configured on purpose: the placeholder resolution is a
 * pure text mapping, independent of any store.
 */
public final class SecretReferenceExpressionEvaluationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveSecretReferenceToItsPlaceholder() {
    // when
    final var record = ENGINE.expression().withExpression("=camunda.secrets.TEST").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("camunda.secrets.TEST");
  }

  @Test
  public void shouldConcatenateSecretReferencePlaceholder() {
    // when
    final var record =
        ENGINE.expression().withExpression("=\"Bearer \" + camunda.secrets.TEST").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("Bearer camunda.secrets.TEST");
  }

  @Test
  public void shouldNotShadowClusterVariables() {
    // given
    ENGINE.clusterVariables().withName("region").setGlobalScope().withValue("\"eu-1\"").create();

    // when
    final var record = ENGINE.expression().withExpression("=camunda.vars.cluster.region").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("eu-1");
  }

  @Test
  public void shouldLetCamundaBodyVariableTakePrecedenceOverSecretPlaceholder() {
    // given - a body variable named `camunda` keeps precedence, so the secret context does not
    // intercept and the missing `token` member resolves to null instead of a placeholder
    // when
    final var record =
        ENGINE
            .expression()
            .withExpression("=camunda.secrets.token")
            .withVariables(Map.of("camunda", Map.of("vars", Map.of("env", Map.of("KEY", "v")))))
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isNull();
  }
}
