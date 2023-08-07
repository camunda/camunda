/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class StandaloneDecisionEvaluationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";
  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_V2 = "/dmn/decision-table_v2.dmn";
  private static final String DECISION_ID = "jedi_or_sith";
  private static final String EXPECTED_DECISION_OUTPUT = "\"Jedi\"";
  private static final String EXPECTED_FAILURE_MSG =
      "Expected to evaluate decision 'jedi_or_sith', but failed to evaluate expression 'lightsaberColor': no variable found for name 'lightsaberColor'";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldEvaluateDecisionById() {
    // given
    ENGINE.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "blue")
            .evaluate();

    // then
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATED);
    assertThat(record.getValue()).hasDecisionOutput(EXPECTED_DECISION_OUTPUT);
  }

  @Test
  public void shouldFailEvaluationOfDecisionById() {
    // given
    ENGINE.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE.decision().ofDecisionId(DECISION_ID).expectFailure().evaluate();

    // then
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.FAILED);
    assertThat(record.getValue()).hasFailedDecisionId(DECISION_ID);
    assertThat(record.getValue()).hasEvaluationFailureMessage(EXPECTED_FAILURE_MSG);
  }

  @Test
  public void shouldEvaluateLatestDecisionById() {
    // given
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();
    final var deploymentEvent =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "blue")
            .evaluate();

    // then
    final int deployedVersion =
        deploymentEvent.getValue().getDecisionsMetadata().stream()
            .filter(decisionRecordValue -> decisionRecordValue.getDecisionId().equals(DECISION_ID))
            .findFirst()
            .get()
            .getVersion();
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATED);
    assertThat(record.getValue()).hasDecisionOutput(EXPECTED_DECISION_OUTPUT);
    assertThat(record.getValue()).hasDecisionVersion(deployedVersion);
  }

  @Test
  public void shouldEvaluateDecisionByKey() {
    // given
    final Record<DeploymentRecordValue> deploymentRecord =
        ENGINE.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();
    final DecisionRecordValue decisionRecord =
        deploymentRecord.getValue().getDecisionsMetadata().stream()
            .filter(decisionRecordValue -> decisionRecordValue.getDecisionId().equals(DECISION_ID))
            .findAny()
            .get();
    final long decisionKey = decisionRecord.getDecisionKey();

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionKey(decisionKey)
            .withVariable("lightsaberColor", "blue")
            .evaluate();

    // then
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATED);
    assertThat(record.getValue()).hasDecisionOutput(EXPECTED_DECISION_OUTPUT);
  }

  @Test
  public void shouldRejectDecisionWithoutIdAndKey() {
    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE.decision().ofDecisionId("").ofDecisionKey(-1L).expectRejection().evaluate();

    // then
    assertThat(record.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATE);
    assertThat(record.getRejectionReason())
        .isEqualTo("Expected either a decision id or a valid decision key, but none provided");
  }

  @Test
  public void shouldRejectDecisionOnFalseId() {
    // given
    final var falseDecisionId = "falseId";

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE.decision().ofDecisionId(falseDecisionId).expectRejection().evaluate();

    // then
    assertThat(record.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATE);
    assertThat(record.getRejectionReason())
        .isEqualTo(
            "Expected to evaluate decision '"
                + falseDecisionId
                + "', but no decision found for id '"
                + falseDecisionId
                + "'");
  }

  @Test
  public void shouldRejectDecisionOnFalseKey() {
    // given
    final var falseDecisionKey = 123L;

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE.decision().ofDecisionKey(falseDecisionKey).expectRejection().evaluate();

    // then
    assertThat(record.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATE);
    assertThat(record.getRejectionReason())
        .isEqualTo(
            "Expected to evaluate decision '"
                + falseDecisionKey
                + "', but no decision found for key '"
                + falseDecisionKey
                + "'");
  }
}
