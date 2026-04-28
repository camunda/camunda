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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ResolveFeelExpressionTest {

  @ClassRule
  public static final EngineRule ENGINE_RULE =
      EngineRule.singlePartition()
          .withEngineConfig(
              e -> {
                // Set it low to speed up shouldRejectResolveWhenExpressionEvaluationTimesOut
                // But not too low to accidentally timeout during other tests
                e.setExpressionEvaluationTimeout(Duration.ofMillis(300));
              });

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveSimpleStringExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=\"hello world\"").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("hello world");
  }

  @Test
  public void shouldResolveSimpleNumberExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=42").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(42);
  }

  @Test
  public void shouldResolveSimpleBooleanExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=true").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(true);
  }

  @Test
  public void shouldResolveNullExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=null").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isNull();
  }

  @Test
  public void shouldResolveListExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=[1, 2, 3]").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(1, 2, 3);
  }

  @Test
  public void shouldResolveObjectExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("={x: 1, y: 2}").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue())
        .isInstanceOf(java.util.Map.class)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("x", 1)
        .containsEntry("y", 2);
  }

  @Test
  public void shouldResolveDateExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=date(\"2024-01-15\")").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("2024-01-15");
  }

  @Test
  public void shouldResolveDateTimeExpression() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=date and time(\"2024-01-15T10:30:00\")")
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("2024-01-15T10:30:00");
  }

  @Test
  public void shouldResolveDurationExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=duration(\"PT5H\")").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("PT5H");
  }

  @Test
  public void shouldResolvePeriodExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=duration(\"P2Y\")").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("P2Y");
  }

  @Test
  public void shouldResolveArithmeticExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=10 + 5 * 2").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(20);
  }

  @Test
  public void shouldResolveComparisonExpression() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=10 > 5").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(true);
  }

  @Test
  public void shouldResolveExpressionWithClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("myVar")
        .setGlobalScope()
        .withValue("\"test-value\"")
        .create();

    // when
    final var record =
        ENGINE_RULE.expression().withExpression("=camunda.vars.cluster.myVar").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("test-value");
  }

  @Test
  public void shouldResolveExpressionWithTenantScopedClusterVariable() {
    // given
    ENGINE_RULE.tenant().newTenant().withTenantId("tenant-1").create();
    ENGINE_RULE
        .clusterVariables()
        .withName("tenantVar")
        .setTenantScope()
        .withTenantId("tenant-1")
        .withValue("42")
        .create();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=camunda.vars.tenant.tenantVar")
            .withTenantId("tenant-1")
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(42);
  }

  @Test
  public void shouldResolveExpressionWithMultipleClusterVariables() {
    // given
    ENGINE_RULE.clusterVariables().withName("var1").setGlobalScope().withValue("10").create();
    ENGINE_RULE.clusterVariables().withName("var2").setGlobalScope().withValue("20").create();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=camunda.vars.env.var1 + camunda.vars.cluster.var2")
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(30);
  }

  @Test
  public void shouldRejectInvalidExpression() {
    // when
    final var record =
        ENGINE_RULE.expression().withExpression("=invalid syntax {{").expectRejection().resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(record.getRejectionReason()).contains("Failed to parse expression");
  }

  @Test
  public void shouldResolveToNullExpressionWithUndefinedVariable() {
    // when
    final var record =
        ENGINE_RULE.expression().withExpression("=camunda.vars.cluster.nonExistentVar").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(null);
  }

  @Test
  public void shouldResolveComplexExpression() {
    // given
    ENGINE_RULE.clusterVariables().withName("x").setGlobalScope().withValue("5").create();
    ENGINE_RULE.clusterVariables().withName("y").setGlobalScope().withValue("10").create();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression(
                "=if camunda.vars.cluster.x > 3 then camunda.vars.env.y * 2 else camunda.vars.cluster.y")
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(20);
  }

  @Test
  public void shouldResolveExpressionWithStringConcatenation() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("firstName")
        .setGlobalScope()
        .withValue("\"John\"")
        .create();
    ENGINE_RULE
        .clusterVariables()
        .withName("lastName")
        .setGlobalScope()
        .withValue("\"Doe\"")
        .create();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression(
                "=camunda.vars.cluster.firstName + \" \" + camunda.vars.cluster.lastName")
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("John Doe");
  }

  @Test
  public void shouldResolveExpressionWithListOperations() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("numbers")
        .setGlobalScope()
        .withValue("[1, 2, 3, 4, 5]")
        .create();

    // when
    final var record =
        ENGINE_RULE.expression().withExpression("=count(camunda.vars.cluster.numbers)").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(5);
  }

  @Test
  public void shouldResolveExpressionReturningEmptyString() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=\"\"").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("");
  }

  @Test
  public void shouldResolveExpressionReturningZeroDotOneAsDouble() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=0.1").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(0.1);
  }

  @Test
  public void shouldResolveExpressionReturningFalse() {
    // when
    final var record = ENGINE_RULE.expression().withExpression("=false").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(false);
  }

  // --- Static Expression Tests (expressions without '=' prefix) ---

  @Test
  public void shouldResolveStaticStringExpression() {
    // when - expression without '=' is treated as static
    final var record = ENGINE_RULE.expression().withExpression("hello world").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("hello world");
  }

  @Test
  public void shouldResolveStaticNumberExpression() {
    // when - numeric string without '=' is treated as static number
    final var record = ENGINE_RULE.expression().withExpression("42").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(42);
  }

  @Test
  public void shouldResolveStaticDecimalExpression() {
    // when - decimal string without '=' is treated as static number
    final var record = ENGINE_RULE.expression().withExpression("3.14159").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(3.14159);
  }

  @Test
  public void shouldResolveStaticNegativeNumberExpression() {
    // when - negative number string without '=' is treated as static number
    final var record = ENGINE_RULE.expression().withExpression("-100").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(-100);
  }

  @Test
  public void shouldResolveStaticStringWithSpecialCharacters() {
    // when - string with special characters without '='
    final var record = ENGINE_RULE.expression().withExpression("hello-world_123!@#$%").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("hello-world_123!@#$%");
  }

  @Test
  public void shouldResolveStaticStringWithSpaces() {
    // when - string with spaces without '='
    final var record = ENGINE_RULE.expression().withExpression("this is a test string").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("this is a test string");
  }

  @Test
  public void shouldResolveStaticScientificNotationAsString() {
    // when - scientific notation is treated as string (not number parsing in StaticExpression)
    final var record = ENGINE_RULE.expression().withExpression("1e10").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    // Note: StaticExpression uses BigDecimal which can parse scientific notation
    assertThat(record.getValue().getResultValue()).isEqualTo(10000000000L);
  }

  @Test
  public void shouldResolveStaticZeroExpression() {
    // when - zero as static number
    final var record = ENGINE_RULE.expression().withExpression("0").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(0);
  }

  @Test
  public void shouldResolveStaticLargeNumberExpression() {
    // when - large number as static
    final var record = ENGINE_RULE.expression().withExpression("9223372036854775807").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void shouldResolveStaticStringThatLooksLikeFeel() {
    // when - string that looks like FEEL but without '=' prefix
    final var record = ENGINE_RULE.expression().withExpression("10 + 5").resolve();

    // then - treated as plain string, not evaluated
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("10 + 5");
  }

  @Test
  public void shouldResolveStaticVerySmallDecimal() {
    // when - very small decimal number
    final var record = ENGINE_RULE.expression().withExpression("0.000001").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(0.000001);
  }

  @Test
  public void shouldResolveStaticBooleanTrueExpression() {
    // when - "true" without '=' is treated as static boolean
    final var record = ENGINE_RULE.expression().withExpression("true").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(true);
  }

  @Test
  public void shouldResolveStaticBooleanFalseExpression() {
    // when - "false" without '=' is treated as static boolean
    final var record = ENGINE_RULE.expression().withExpression("false").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(false);
  }

  @Test
  public void shouldResolveStaticNullExpression() {
    // when - "null" without '=' is treated as static null
    final var record = ENGINE_RULE.expression().withExpression("null").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isNull();
  }

  @Test
  public void shouldResolveStaticStringTrueWithDifferentCase() {
    // when - "True" (different case) should be treated as string, not boolean
    final var record = ENGINE_RULE.expression().withExpression("True").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("True");
  }

  @Test
  public void shouldResolveStaticStringNullWithDifferentCase() {
    // when - "Null" (different case) should be treated as string, not null
    final var record = ENGINE_RULE.expression().withExpression("Null").resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("Null");
  }

  @Test
  public void shouldRejectResolveWhenExpressionEvaluationTimesOut() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=for x in 0..1000000 return for y in 0..x return x + y")
            .expectRejection()
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.PROCESSING_ERROR)
        .hasRejectionReason(
            """
            Expected to evaluate expression but timed out after 300 ms: \
            'for x in 0..1000000 return for y in 0..x return x + y'""");
  }

  @Test
  public void shouldResolveExpressionWithContext() {
    // given
    ENGINE_RULE.clusterVariables().withName("input").setGlobalScope().withValue("5").create();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=camunda.vars.cluster.input * 2 + myVar")
            .withVariables(Map.of("myVar", 5))
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(15);
    // context is passed through
    assertThat(record.getValue().getVariables()).isEqualTo(Map.of("myVar", 5));
  }

  @Test
  public void shouldResolveWithWarningIfContextVarHasAWrongTypeOrMissing() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=a * 2 + b")
            .withVariables(Map.of("a", "not a number"))
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(null);
    assertThat(record.getValue().getWarnings())
        .containsExactlyInAnyOrder(
            "Can't multiply '\"not a number\"' by '2'",
            "No variable found with name 'b'",
            "Can't add 'null' to 'null'");
  }

  @Test
  public void shouldResolveExpressionWithExplicitContextAsNull() {
    // when
    final var record =
        ENGINE_RULE.expression().withExpression("=5 + 3").withVariables(null).resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(8);
  }

  @Test
  public void shouldResolveDateTimeExpressionWithContextVariable() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("= date and time(myDateTime)")
            .withVariables(Map.of("myDateTime", "2024-01-15T10:30:00"))
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("2024-01-15T10:30:00");
  }

  @Test
  public void shouldResolveExpressionWithListInContext() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=count(myList)")
            .withVariables(Map.of("myList", java.util.List.of(1, 2, 3, 4, 5)))
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(5);
  }

  @Test
  public void shouldResolveExpressionWithNestedContext() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=person.name + \" is \" + string(person.age) + \" years old\"")
            .withVariables(Map.of("person", Map.of("name", "Alice", "age", 30)))
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("Alice is 30 years old");
  }

  @Test
  public void shouldResolveVariablesFromProcessInstanceScope() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("processWithVars")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("processWithVars")
            .withVariables(Map.of("myVar", "hello from process"))
            .create();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=myVar")
            .withProcessInstanceKey(processInstanceKey)
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("hello from process");
  }

  @Test
  public void shouldResolveVariablesFromElementInstanceScope() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("processForElement")
                .startEvent()
                .serviceTask("elementTask", t -> t.zeebeJobType("elementTest"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("processForElement")
            .withVariables(Map.of("elemVar", 42))
            .create();

    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=elemVar")
            .withElementInstanceKey(elementInstanceKey)
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(42);
  }

  @Test
  public void shouldResolveVariableLocalToIntermediateCatchEventScope() {
    // given - a process waiting on an intermediate catch event (the typical Connector use case)
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("processWithCatchEvent")
                .startEvent()
                .intermediateCatchEvent("catch-event", e -> e.timerWithDuration("PT1H"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId("processWithCatchEvent").create();

    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .getFirst()
            .getKey();

    // a variable created by the catch event itself (local to its own scope),
    // simulating a Connector writing its result onto the catch event instance
    ENGINE_RULE
        .variables()
        .ofScope(elementInstanceKey)
        .withDocument(Map.of("catchVar", "hello from catch event"))
        .withLocalSemantic()
        .update();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=catchVar")
            .withElementInstanceKey(elementInstanceKey)
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo("hello from catch event");
  }

  @Test
  public void shouldResolveVariableLocalToServiceTaskWithBoundaryEventScope() {
    // given - a service task with a boundary event (the typical Connector use case)
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("processWithBoundaryEvent")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .boundaryEvent("boundary-event", b -> b.timerWithDuration("PT1H"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId("processWithBoundaryEvent").create();

    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    // a variable created by the service task itself (local to its own scope),
    // simulating a Connector writing its result onto the service task instance
    ENGINE_RULE
        .variables()
        .ofScope(elementInstanceKey)
        .withDocument(Map.of("boundaryVar", 123))
        .withLocalSemantic()
        .update();

    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=boundaryVar")
            .withElementInstanceKey(elementInstanceKey)
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(123);
  }

  @Test
  public void shouldRejectWhenBothProcessInstanceKeyAndElementInstanceKeyProvided() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=1")
            .withProcessInstanceKey(1L)
            .withElementInstanceKey(2L)
            .expectRejection()
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(record.getRejectionReason())
        .contains("Either 'processInstanceKey' or 'elementInstanceKey' must be provided, not both");
  }

  @Test
  public void shouldRejectWhenProcessInstanceKeyNotFound() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=1")
            .withProcessInstanceKey(999999L)
            .expectRejection()
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.NOT_FOUND);
    assertThat(record.getRejectionReason()).contains("999999");
  }

  @Test
  public void shouldRejectWhenElementInstanceKeyNotFound() {
    // when
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=1")
            .withElementInstanceKey(999999L)
            .expectRejection()
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.NOT_FOUND);
    assertThat(record.getRejectionReason()).contains("999999");
  }

  @Test
  public void shouldPrioritizeBodyVariablesOverProcessInstanceVariables() {
    // given - process instance has "score = 1"
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("priorityBodyOverProcess")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("priorityBodyOverProcess")
            .withVariables(Map.of("score", 1))
            .create();

    // when - body also has "score = 99", body takes precedence
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=score")
            .withProcessInstanceKey(processInstanceKey)
            .withVariables(Map.of("score", 99))
            .resolve();

    // then - body variable wins
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(99);
  }

  @Test
  public void shouldResolveProcessScopeVariablesWhenElementInstanceKeyProvided() {
    // given - variable "score" is on the process instance scope (visible from any element in it)
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("elementScopeResolvesProcessVars")
                .startEvent()
                .serviceTask("elementTask", t -> t.zeebeJobType("elementTest"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("elementScopeResolvesProcessVars")
            .withVariables(Map.of("score", 42))
            .create();

    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    // when - element instance key is provided; process-scope variable is visible from it
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=score")
            .withElementInstanceKey(elementInstanceKey)
            .resolve();

    // then - process-scope variable is resolved through the element instance
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(42);
  }

  @Test
  public void shouldPrioritizeProcessInstanceVariablesOverClusterVariables() {
    // given - cluster has "score = 1", process instance also has "score = 99"
    ENGINE_RULE.clusterVariables().withName("score").setGlobalScope().withValue("1").create();

    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("priorityProcessOverCluster")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("priorityProcessOverCluster")
            .withVariables(Map.of("score", 99))
            .create();

    // when - process instance variable has "score", takes precedence over cluster
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=score")
            .withProcessInstanceKey(processInstanceKey)
            .resolve();

    // then - process instance variable wins over cluster variable
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATED)
        .hasRecordType(RecordType.EVENT);
    assertThat(record.getValue().getResultValue()).isEqualTo(99);
  }

  @Test
  public void shouldRejectWhenTenantDoesNotMatchProcessInstance() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("processTenantA")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId("processTenantA").create();

    // when - request with a tenant that does not own this process instance
    final var record =
        ENGINE_RULE
            .expression()
            .withExpression("=1")
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId("wrong-tenant")
            .expectRejection()
            .resolve();

    // then
    Assertions.assertThat(record)
        .hasIntent(ExpressionIntent.EVALUATE)
        .hasRejectionType(RejectionType.NOT_FOUND);
    assertThat(record.getRejectionReason()).contains("wrong-tenant");
  }
}
