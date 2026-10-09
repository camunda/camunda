/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the engine declares protection modes on each evaluated decision input whose expression
 * references a variable matching the configured sensitive-variable patterns (default {@code
 * sensitive_.*}, mode {@code REDACT}), while keeping the plain value on the logged record.
 */
public class DecisionInputProtectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String DECISION_ID = "sensitive_inputs_decision";
  private static final Map<String, Object> VARIABLES =
      Map.of("sensitive_ssn", "123-45-6789", "sensitive_salary", 120000, "lightsaberColor", "blue");

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void deployDecision() {
    ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/decision-table-with-sensitive-inputs.dmn")
        .deploy();
  }

  @Test
  public void shouldDeclareProtectionOnInputsReferencingSensitiveVariables() {
    // when
    final var record =
        ENGINE.decision().ofDecisionId(DECISION_ID).withVariables(VARIABLES).evaluate();

    // then -- a derived input counts too, since its value can still reveal the sensitive variable
    assertThat(record.getValue().getEvaluatedDecisions().getFirst().getEvaluatedInputs())
        .extracting(EvaluatedInputValue::getInputId, EvaluatedInputValue::getProtectionModes)
        .containsExactly(
            tuple("Input_Direct", Set.of(ProtectionMode.REDACT)),
            tuple("Input_Derived", Set.of(ProtectionMode.REDACT)),
            tuple("Input_Plain", Set.of()));
  }

  @Test
  public void shouldKeepPlainInputValuesOnTheLoggedRecord() {
    // when
    final var record =
        ENGINE.decision().ofDecisionId(DECISION_ID).withVariables(VARIABLES).evaluate();

    // then -- redaction is applied at export, so the engine's own record keeps the real values
    assertThat(record.getValue().getEvaluatedDecisions().getFirst().getEvaluatedInputs())
        .extracting(EvaluatedInputValue::getInputId, EvaluatedInputValue::getInputValue)
        .containsExactly(
            tuple("Input_Direct", "\"123-45-6789\""),
            tuple("Input_Derived", "true"),
            tuple("Input_Plain", "\"blue\""));
  }
}
