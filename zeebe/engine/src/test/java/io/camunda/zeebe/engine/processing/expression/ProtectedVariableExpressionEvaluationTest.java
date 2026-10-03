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
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the expression endpoint does not resolve process instance variables matching the
 * configured sensitive-variable patterns (default {@code sensitive_.*}): only those variables are
 * withheld, the rest of the expression evaluates as before, and a warning names each one.
 */
public class ProtectedVariableExpressionEvaluationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "protected-expression";
  private static final String PROTECTED_WARNING =
      "Variable 'sensitive_ssn' is protected and was not resolved; it was evaluated as null";

  private static long processInstanceKey;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void startInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("wait"))
                .endEvent()
                .done())
        .deploy();
    processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("sensitive_ssn", "123-45-6789", "customerId", "C-42"))
            .create();
  }

  @Test
  public void shouldNotResolveSensitiveVariable() {
    // when
    final var record =
        ENGINE
            .expression()
            .withExpression("=sensitive_ssn")
            .withScopeKey(processInstanceKey)
            .resolve();

    // then
    assertThat(record.getValue().getResultValue()).isNull();
    assertThat(record.getValue().getWarnings()).contains(PROTECTED_WARNING);
  }

  @Test
  public void shouldWithholdOnlyTheSensitiveVariable() {
    // when -- one expression reading both a plain and a sensitive variable
    final var record =
        ENGINE
            .expression()
            .withExpression("={id: customerId, ssn: sensitive_ssn}")
            .withScopeKey(processInstanceKey)
            .resolve();

    // then -- the plain part of the result is untouched
    final var expected = new HashMap<String, Object>();
    expected.put("id", "C-42");
    expected.put("ssn", null);
    assertThat(record.getValue().getResultValue()).isEqualTo(expected);
    assertThat(record.getValue().getWarnings()).contains(PROTECTED_WARNING);
  }

  @Test
  public void shouldEvaluateAsBeforeWithoutSensitiveVariable() {
    // when
    final var record =
        ENGINE
            .expression()
            .withExpression("=customerId")
            .withScopeKey(processInstanceKey)
            .resolve();

    // then
    assertThat(record.getValue().getResultValue()).isEqualTo("C-42");
    assertThat(record.getValue().getWarnings()).isEmpty();
  }

  @Test
  public void shouldResolveSensitiveNameFromRequestBody() {
    // when -- a body variable is the caller's own value, so there is nothing to protect
    final var record =
        ENGINE
            .expression()
            .withExpression("=sensitive_ssn")
            .withVariables(Map.of("sensitive_ssn", "provided-by-caller"))
            .withScopeKey(processInstanceKey)
            .resolve();

    // then
    assertThat(record.getValue().getResultValue()).isEqualTo("provided-by-caller");
    assertThat(record.getValue().getWarnings()).doesNotContain(PROTECTED_WARNING);
  }
}
