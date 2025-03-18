/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareStandaloneDecisionEvaluationTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_WITH_ASSERTIONS =
      "/dmn/decision-table-with-assertions.dmn";
  private static final String DECISION_ID = "jedi_or_sith";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private final String tenantOne = "foo";
  private final String tenantTwo = "bar";

  private Map<String, DecisionRecordValue> deployedDecisionsById;

  private void deployForTenant(final String process, final String tenant) {
    final var deployment =
        ENGINE.deployment().withXmlClasspathResource(process).withTenantId(tenant).deploy();

    deployedDecisionsById =
        deployment.getValue().getDecisionsMetadata().stream()
            .collect(Collectors.toMap(DecisionRecordValue::getDecisionId, Function.identity()));
  }

  @Test
  public void shouldEvaluateDecision() {
    // given
    deployForTenant(DMN_DECISION_TABLE, tenantOne);

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "blue")
            .withTenant(tenantOne)
            .evaluate();

    final var calledDecision = deployedDecisionsById.get(DECISION_ID);

    // then
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATED);

    final var decisionEvaluationValue = record.getValue();
    assertThat(decisionEvaluationValue)
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionId(calledDecision.getDecisionId())
        .hasDecisionName(calledDecision.getDecisionName())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionRequirementsKey(calledDecision.getDecisionRequirementsKey())
        .hasDecisionRequirementsId(calledDecision.getDecisionRequirementsId())
        .hasTenantId(tenantOne);

    final var evaluatedDecision = decisionEvaluationValue.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecision).hasTenantId(tenantOne);
  }

  @Test
  public void shouldWriteDecisionEvaluationEventIfEvaluationFailed() {
    // given
    deployForTenant(DMN_DECISION_TABLE_WITH_ASSERTIONS, tenantOne);

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withTenant(tenantOne)
            .expectFailure()
            .evaluate();

    final var calledDecision = deployedDecisionsById.get(DECISION_ID);

    // then
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.FAILED);
    assertThat(record.getValue())
        .hasDecisionKey(calledDecision.getDecisionKey())
        .hasDecisionId(calledDecision.getDecisionId())
        .hasDecisionName(calledDecision.getDecisionName())
        .hasDecisionVersion(calledDecision.getVersion())
        .hasDecisionRequirementsKey(calledDecision.getDecisionRequirementsKey())
        .hasDecisionRequirementsId(calledDecision.getDecisionRequirementsId())
        .hasTenantId(tenantOne)
        .hasEvaluationFailureMessage(
            """
            Expected to evaluate decision 'jedi_or_sith', but \
            Assertion failure on evaluate the expression \
            'assert(lightsaberColor, lightsaberColor != null)': The condition is not fulfilled""");

    final var evaluatedDecision = record.getValue().getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecision).hasTenantId(tenantOne);
  }

  @Test
  public void shouldRejectDecisionEvaluationWithCustomTenant() {
    // given
    deployForTenant(DMN_DECISION_TABLE, tenantOne);

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "blue")
            .withTenant(tenantTwo)
            .expectRejection()
            .evaluate();

    // then
    assertThat(record.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATE);
    assertThat(record.getRejectionReason())
        .isEqualTo(
            "Expected to evaluate decision '"
                + DECISION_ID
                + "', but no decision found for id '"
                + DECISION_ID
                + "'");
    assertThat(record.getValue()).hasTenantId(tenantTwo);
  }

  @Test
  public void shouldRejectDecisionEvaluationWithDefaultTenant() {
    // given
    deployForTenant(DMN_DECISION_TABLE, tenantOne);

    // when
    final Record<DecisionEvaluationRecordValue> record =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "blue")
            .expectRejection()
            .evaluate();

    // then
    assertThat(record.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(record.getIntent()).isEqualTo(DecisionEvaluationIntent.EVALUATE);
    assertThat(record.getRejectionReason())
        .isEqualTo(
            "Expected to evaluate decision '"
                + DECISION_ID
                + "', but no decision found for id '"
                + DECISION_ID
                + "'");
    assertThat(record.getValue()).hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }
}
