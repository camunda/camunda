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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.value.ExpressionScopeType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EvaluateExpressionProcessorTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldEvaluateSimpleExpression() {
    // given
    final var expression = "= order.amount > 100";
    final var context = Map.<String, Object>of();

    // when
    final var evaluationResult =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .evaluate();

    // then
    Assertions.assertThat(evaluationResult).hasIntent(ExpressionIntent.EVALUATED);
    final var record = evaluationResult.getValue();
    assertThat(record.getExpression()).isEqualTo(expression);
    assertThat(record.getScopeType()).isEqualTo(ExpressionScopeType.NONE);
  }

  @Test
  public void shouldEvaluateExpressionWithProvidedContext() {
    // given
    final var expression = "x + y";
    final var context = Map.<String, Object>of("x", 10, "y", 20);

    // when
    final var evaluationResult =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .evaluate();

    // then
    Assertions.assertThat(evaluationResult).hasIntent(ExpressionIntent.EVALUATED);
    assertThat(evaluationResult.getValue().getExpression()).isEqualTo(expression);
  }

  //  @Test
  //  public void shouldEvaluateExpressionWithClusterScope() {
  //    // given - create a cluster variable
  //    ENGINE.clusterVariable().withName("clusterVar").withValue("\"test\"").create();
  //
  //    final var expression = "clusterVar";
  //    final var context = Map.<String, Object>of();
  //
  //    // when
  //    final var evaluationResult =
  //        ENGINE
  //            .expression()
  //            .withExpression(expression)
  //            .withScope(ExpressionScopeType.CLUSTER)
  //            .withContext(context)
  //            .evaluate();
  //
  //    // then
  //    Assertions.assertThat(evaluationResult)
  //        .hasIntent(ExpressionIntent.EVALUATED);
  //
  // assertThat(evaluationResult.getValue().getScopeType()).isEqualTo(ExpressionScopeType.CLUSTER);
  //  }

  @Test
  public void shouldEvaluateExpressionWithProcessInstanceScope() {
    // given - create a process instance with variables
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariable("processVar", 42).create();

    final var expression = "processVar + 8";
    final var context = Map.<String, Object>of();

    // when
    final var evaluationResult =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.PROCESS_INSTANCE)
            .withProcessInstanceKey(processInstanceKey)
            .withContext(context)
            .evaluate();

    // then
    Assertions.assertThat(evaluationResult).hasIntent(ExpressionIntent.EVALUATED);
    final var record = evaluationResult.getValue();
    assertThat(record.getScopeType()).isEqualTo(ExpressionScopeType.PROCESS_INSTANCE);
    assertThat(record.getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  //  @Test
  //  public void shouldGiveProvidedContextPriorityOverClusterVariables() {
  //    // given - cluster variable with same name
  //    ENGINE.clusterVariable().withName("priority").withValue("\"cluster\"").create();
  //
  //    final var expression = "priority";
  //    final var context = Map.<String, Object>of("priority", "provided");
  //
  //    // when
  //    final var evaluationResult =
  //        ENGINE
  //            .expression()
  //            .withExpression(expression)
  //            .withScope(ExpressionScopeType.CLUSTER)
  //            .withContext(context)
  //            .evaluate();
  //
  //    // then
  //    Assertions.assertThat(evaluationResult).hasIntent(ExpressionIntent.EVALUATED);
  //    // The result should be "provided" not "cluster"
  //  }

  @Test
  public void shouldRejectEmptyExpression() {
    // given
    final var expression = "";
    final var context = Map.<String, Object>of();

    // when
    final var rejection =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expression must not be null or empty.");
  }

  @Test
  public void shouldRejectInvalidExpression() {
    // given
    final var expression = "invalid syntax {{";
    final var context = Map.<String, Object>of();

    // when
    final var rejection =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("Failed to parse expression");
  }

  @Test
  public void shouldRejectExpressionExceedingMaxLength() {
    // given - expression longer than MAX_EXPRESSION_LENGTH (10,000 characters)
    final var expression = "x".repeat(10_001);
    final var context = Map.<String, Object>of();

    // when
    final var rejection =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expression exceeds maximum length of 10000 characters.");
  }

  @Test
  public void shouldRejectProcessInstanceScopeWithoutProcessInstanceKey() {
    // given
    final var expression = "x";
    final var context = Map.<String, Object>of("x", 1);

    // when
    final var rejection =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.PROCESS_INSTANCE)
            .withContext(context)
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Process instance key must be positive for PROCESS_INSTANCE scope.");
  }

  @Test
  public void shouldRejectProcessInstanceScopeWithNonExistentProcessInstance() {
    // given
    final var expression = "x";
    final var context = Map.<String, Object>of("x", 1);
    final var nonExistentKey = 999999L;

    // when
    final var rejection =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.PROCESS_INSTANCE)
            .withProcessInstanceKey(nonExistentKey)
            .withContext(context)
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason()).contains("Process instance with key");
  }

  @Test
  public void shouldRejectExpressionWithRuntimeError() {
    // given - expression that will fail at runtime
    final var expression = "1 / 0";
    final var context = Map.<String, Object>of();

    // when
    final var rejection =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .expectRejection()
            .evaluate();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldEvaluateComplexExpression() {
    // given
    final var expression =
        "if amount > 1000 then \"high\" else if amount > 100 then \"medium\" else \"low\"";
    final var context = Map.<String, Object>of("amount", 500);

    // when
    final var evaluationResult =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .evaluate();

    // then
    Assertions.assertThat(evaluationResult).hasIntent(ExpressionIntent.EVALUATED);
    assertThat(evaluationResult.getValue().getExpression()).isEqualTo("medium");
  }

  @Test
  public void shouldEvaluateExpressionWithNestedContext() {
    // given
    final var expression = "customer.name";
    final var context = Map.<String, Object>of("customer", Map.of("name", "John Doe", "age", 30));

    // when
    final var evaluationResult =
        ENGINE
            .expression()
            .withExpression(expression)
            .withScope(ExpressionScopeType.NONE)
            .withContext(context)
            .evaluate();

    // then
    Assertions.assertThat(evaluationResult).hasIntent(ExpressionIntent.EVALUATED);
  }
}
