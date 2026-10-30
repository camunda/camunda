/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue.ExpressionSecretReferenceValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the expression endpoint reports which {@code camunda.secrets.<name>} references it
 * resolved from trusted sources — a reference used directly in the expression, or one carried by a
 * {@code SECRET_REFERENCE}-kind cluster variable — while never reporting references that only
 * appear in untrusted input (request-body variables or plain, JSON-kind cluster variables). The
 * latter is the security invariant: reporting them would let a caller resolve an injected secret.
 */
public class ExpressionReferencedSecretsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldReportSecretReferenceUsedDirectlyInExpression() {
    // when
    final var record = ENGINE.expression().withExpression("=camunda.secrets.token").resolve();

    // then the reference resolves to its placeholder and is reported
    assertThat(record.getValue().getResultValue()).isEqualTo("camunda.secrets.token");
    assertThat(record.getValue().getReferencedSecrets())
        .extracting(
            ExpressionSecretReferenceValue::getStoreId,
            ExpressionSecretReferenceValue::getSecretReference)
        .containsExactly(tuple("default", "token"));
  }

  @Test
  public void shouldReportSecretReferenceCarriedByClusterVariable() {
    // given a SECRET_REFERENCE-kind cluster variable
    ENGINE
        .clusterVariables()
        .withName("SECRET_CLUSTER_VAR")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("auth", "camunda.secrets.dbPassword"))
        .create();

    // when the expression reads it
    final var record =
        ENGINE.expression().withExpression("=camunda.vars.cluster.SECRET_CLUSTER_VAR").resolve();

    // then the cluster variable's secret reference is reported
    assertThat(record.getValue().getReferencedSecrets())
        .extracting(
            ExpressionSecretReferenceValue::getStoreId,
            ExpressionSecretReferenceValue::getSecretReference)
        .containsExactly(tuple("default", "dbPassword"));
  }

  @Test
  public void shouldReportSecretReferenceFromNestedClusterVariableAccess() {
    // given a SECRET_REFERENCE-kind cluster variable holding a nested reference
    ENGINE
        .clusterVariables()
        .withName("NESTED_SECRET_CLUSTER_VAR")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("auth", "camunda.secrets.dbPassword"))
        .create();

    // when the expression reads only a nested member of it
    final var record =
        ENGINE
            .expression()
            .withExpression("=camunda.vars.cluster.NESTED_SECRET_CLUSTER_VAR.auth")
            .resolve();

    // then the nested reference resolves to its placeholder and is still reported
    assertThat(record.getValue().getResultValue()).isEqualTo("camunda.secrets.dbPassword");
    assertThat(record.getValue().getReferencedSecrets())
        .extracting(
            ExpressionSecretReferenceValue::getStoreId,
            ExpressionSecretReferenceValue::getSecretReference)
        .containsExactly(tuple("default", "dbPassword"));
  }

  @Test
  public void shouldNotReportSecretReferenceFromPlainClusterVariable() {
    // given a plain (JSON-kind) cluster variable whose value merely looks like a reference
    ENGINE
        .clusterVariables()
        .withName("PLAIN_CLUSTER_VAR")
        .setGlobalScope()
        .withValue("\"camunda.secrets.evil\"")
        .create();

    // when the expression reads it
    final var record =
        ENGINE.expression().withExpression("=camunda.vars.cluster.PLAIN_CLUSTER_VAR").resolve();

    // then nothing is reported — resolving it would leak an injected secret
    assertThat(record.getValue().getResultValue()).isEqualTo("camunda.secrets.evil");
    assertThat(record.getValue().getReferencedSecrets()).isEmpty();
  }

  @Test
  public void shouldNotReportSecretReferenceFromRequestBodyVariable() {
    // when an untrusted request-body variable holds a reference-looking string
    final var record =
        ENGINE
            .expression()
            .withExpression("=injected")
            .withVariables(Map.of("injected", "camunda.secrets.evil"))
            .resolve();

    // then nothing is reported
    assertThat(record.getValue().getResultValue()).isEqualTo("camunda.secrets.evil");
    assertThat(record.getValue().getReferencedSecrets()).isEmpty();
  }

  @Test
  public void shouldReportNoSecretsForExpressionWithoutReferences() {
    // when
    final var record = ENGINE.expression().withExpression("=1 + 2").resolve();

    // then
    assertThat(record.getValue().getReferencedSecrets()).isEmpty();
  }
}
