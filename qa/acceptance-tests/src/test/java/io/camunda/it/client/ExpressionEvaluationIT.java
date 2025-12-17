/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ExpressionEvaluationIT {

  private static CamundaClient camundaClient;

  // ============ STATIC EXPRESSION TESTS (LITERALS) ============

  @Test
  void shouldEvaluateStaticStringLiteral() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("Hello World").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getExpression()).isEqualTo("Hello World");
    assertThat(response.getResult()).isEqualTo("Hello World");
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateStaticIntegerLiteral() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("42").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(42);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateStaticDoubleLiteral() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("3.14").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(3.14);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateStaticBooleanTrueLiteral() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("true").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(true);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateStaticBooleanFalseLiteral() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("false").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(false);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateStaticNullLiteral() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("null").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isNull();
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateStaticNestedStructure() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("{person: {name: \"Bob\", scores: [10, 20, 30]}}")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("{person: {name: \"Bob\", scores: [10, 20, 30]}}");
    assertThat(response.getWarnings()).isEmpty();
  }

  // ============ BASIC EXPRESSION TESTS (NO VARIABLES) ============

  @Test
  void shouldEvaluateSimpleIntegerExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=1 + 2").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getExpression()).isEqualTo("=1 + 2");
    assertThat(response.getResult()).isEqualTo(3);
    assertThat(response.getWarnings()).isEmpty();
  }

  @Test
  void shouldEvaluateSimpleDoubleExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=1.5 + 2.6").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(4.1);
  }

  @Test
  void shouldEvaluateSimpleStringExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=\"Hello\" + \" \" + \"World\"")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("Hello World");
  }

  @Test
  void shouldEvaluateBooleanExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=true and false").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(false);
  }

  @Test
  void shouldEvaluateBooleanExpressionTrue() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=true or false").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(true);
  }

  @Test
  void shouldEvaluateComparisonExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=5 > 3").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(true);
  }

  @Test
  void shouldEvaluateListExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=[1, 2, 3]").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(List.of(1, 2, 3));
  }

  @Test
  void shouldEvaluateContextExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("={name: \"John\", age: 30}")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(Map.of("name", "John", "age", 30));
  }

  @Test
  void shouldEvaluateIfExpression() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=if 10 > 5 then \"greater\" else \"smaller\"")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("greater");
  }

  @Test
  void shouldEvaluateMathFunctions() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient.newEvaluateExpressionCommand().expression("=abs(-5)").send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(5);
  }

  @Test
  void shouldEvaluateStringFunctions() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=upper case(\"hello\")")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("HELLO");
  }

  @Test
  void shouldRejectInvalidExpression() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newEvaluateExpressionCommand()
                    .expression("={{invalid_syntax}")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("400");
  }

  @Test
  void shouldEvaluateExpressionWithWarnings() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=invalid_function()")
            .send()
            .join();
    // then
    assertThat(response).isNotNull();
    assertThat(response.getWarnings())
        .contains("No function found with name 'invalid_function' and 0 parameters");
  }

  // ============ GLOBAL SCOPED CLUSTER VARIABLE TESTS ============

  @Test
  void shouldEvaluateExpressionWithGlobalClusterVariableInteger() {
    // given
    final String variableName = "globalIntVar_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, 42)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName + " + 8")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(50);
  }

  @Test
  void shouldEvaluateExpressionWithGlobalClusterVariableDouble() {
    // given
    final String variableName = "globalDoubleVar_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, 3.14)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName + " * 2")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(6.28);
  }

  @Test
  void shouldEvaluateExpressionWithGlobalClusterVariableString() {
    // given
    final String variableName = "globalStringVar_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "Hello")
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName + " + \" World\"")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("Hello World");
  }

  @Test
  void shouldEvaluateExpressionWithGlobalClusterVariableObject() {
    // given
    final String variableName = "globalObjectVar_" + UUID.randomUUID().toString().replace("-", "");
    final Map<String, Object> objectValue = Map.of("name", "John", "age", 30);
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, objectValue)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName + ".name")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("John");
  }

  @Test
  void shouldEvaluateExpressionWithGlobalClusterVariableNestedObject() {
    // given
    final String variableName = "globalNestedVar_" + UUID.randomUUID().toString().replace("-", "");
    final Map<String, Object> nestedObject =
        Map.of("person", Map.of("name", "Jane", "address", Map.of("city", "Berlin")));
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, nestedObject)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName + ".person.address.city")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("Berlin");
  }

  // ============ TENANT SCOPED CLUSTER VARIABLE TESTS ============

  @Test
  void shouldEvaluateExpressionWithTenantClusterVariableInteger() {
    // given
    final String variableName = "tenantIntVar_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantId = "<default>";
    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, 100)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName + " - 50")
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(50);
  }

  @Test
  void shouldEvaluateExpressionWithTenantClusterVariableString() {
    // given
    final String variableName = "tenantStringVar_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantId = "<default>";
    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, "Tenant")
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName + " + \" Value\"")
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("Tenant Value");
  }

  @Test
  void shouldEvaluateExpressionWithTenantClusterVariableObject() {
    // given
    final String variableName = "tenantObjectVar_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantId = "<default>";
    final Map<String, Object> objectValue = Map.of("product", "Widget", "price", 19.99);
    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, objectValue)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName + ".product")
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("Widget");
  }

  @Test
  void shouldEvaluateExpressionWithTenantClusterVariableNestedObject() {
    // given
    final String variableName = "tenantNestedVar_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantId = "<default>";
    final Map<String, Object> nestedObject =
        Map.of("config", Map.of("settings", Map.of("enabled", true, "maxRetries", 3)));
    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, nestedObject)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName + ".config.settings.maxRetries")
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(3);
  }

  // ============ ENV (MERGED CONTEXT) TESTS ============

  @Test
  void shouldEvaluateExpressionWithEnvVariableFromGlobalScope() {
    // given - create a global variable
    final String variableName = "envGlobalVar_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    // when - access via env (merged context)
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("GlobalValue");
  }

  @Test
  void shouldEvaluateExpressionWithEnvVariableFromTenantScope() {
    // given - create a tenant variable
    final String variableName = "envTenantVar_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantId = "<default>";
    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, "TenantValue")
        .send()
        .join();

    // when - access via env (merged context) with tenant
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("TenantValue");
  }

  @Test
  void shouldEvaluateExpressionWithEnvVariableTenantOverridesGlobal() {
    // given - create both global and tenant variable with same name
    final String variableName = "envOverrideVar_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantId = "<default>";

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    camundaClient
        .newTenantScopedClusterVariableCreateRequest(tenantId)
        .create(variableName, "TenantValue")
        .send()
        .join();

    // when - access via env (merged context) with tenant - should get tenant value
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(tenantId)
            .send()
            .join();

    // then - tenant value should override global value
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("TenantValue");
  }

  @Test
  void shouldEvaluateExpressionWithEnvNestedVariable() {
    // given
    final String variableName = "envNestedVar_" + UUID.randomUUID().toString().replace("-", "");
    final Map<String, Object> nestedObject =
        Map.of("database", Map.of("host", "localhost", "port", 5432));
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, nestedObject)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName + ".database.host")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("localhost");
  }

  // ============ COMPLEX EXPRESSION TESTS WITH CLUSTER VARIABLES ============

  @Test
  void shouldEvaluateComplexExpressionWithMultipleGlobalVariables() {
    // given
    final String priceVar = "price_" + UUID.randomUUID().toString().replace("-", "");
    final String quantityVar = "quantity_" + UUID.randomUUID().toString().replace("-", "");
    final String discountVar = "discount_" + UUID.randomUUID().toString().replace("-", "");

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(priceVar, 100)
        .send()
        .join();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(quantityVar, 5)
        .send()
        .join();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(discountVar, 0.1)
        .send()
        .join();

    // when - calculate total with discount
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression(
                "=camunda.vars.cluster."
                    + priceVar
                    + " * camunda.vars.cluster."
                    + quantityVar
                    + " * (1 - camunda.vars.cluster."
                    + discountVar
                    + ")")
            .send()
            .join();

    // then - 100 * 5 * 0.9 = 450
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(450);
  }

  @Test
  void shouldEvaluateConditionalExpressionWithClusterVariable() {
    // given
    final String thresholdVar = "threshold_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(thresholdVar, 50)
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression(
                "=if 75 > camunda.vars.cluster." + thresholdVar + " then \"above\" else \"below\"")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("above");
  }

  @Test
  void shouldEvaluateExpressionWithClusterVariableInList() {
    // given
    final String listVar = "items_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(listVar, List.of(1, 2, 3, 4, 5))
        .send()
        .join();

    // when - sum of list
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=sum(camunda.vars.cluster." + listVar + ")")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(15);
  }

  @Test
  void shouldEvaluateExpressionWithClusterVariableStringConcatenation() {
    // given
    final String firstNameVar = "firstName_" + UUID.randomUUID().toString().replace("-", "");
    final String lastNameVar = "lastName_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(firstNameVar, "John")
        .send()
        .join();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(lastNameVar, "Doe")
        .send()
        .join();

    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression(
                "=camunda.vars.cluster."
                    + firstNameVar
                    + " + \" \" + camunda.vars.cluster."
                    + lastNameVar)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("John Doe");
  }

  @Test
  void shouldEvaluateExpressionWithNestedFunctionCalls() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=upper case(substring(\"hello world\", 1, 5))")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo("HELLO");
  }

  @Test
  void shouldEvaluateExpressionWithForLoop() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=for i in [1, 2, 3] return i * 2")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(List.of(2, 4, 6));
  }

  @Test
  void shouldEvaluateExpressionWithSomeQuantifier() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=some x in [1, 2, 3] satisfies x > 2")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(true);
  }

  @Test
  void shouldEvaluateExpressionWithEveryQuantifier() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=every x in [1, 2, 3] satisfies x > 0")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(true);
  }

  @Test
  void shouldEvaluateExpressionWithFilterList() {
    // when
    final EvaluateExpressionResponse response =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=[1, 2, 3, 4, 5][item > 2]")
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(List.of(3, 4, 5));
  }

  // ============ ERROR HANDLING TESTS ============

  @Test
  void shouldRejectExpressionWithNonExistentClusterVariable() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newEvaluateExpressionCommand()
                    .expression("=camunda.vars.cluster.nonExistentVar_" + UUID.randomUUID())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class);
  }

  @Test
  void shouldRejectNullExpression() {
    // when / then
    assertThatThrownBy(
            () -> camundaClient.newEvaluateExpressionCommand().expression(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expression must not be null");
  }
}
