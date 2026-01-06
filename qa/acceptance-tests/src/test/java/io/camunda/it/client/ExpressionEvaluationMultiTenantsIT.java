/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.createTenant;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ExpressionEvaluationMultiTenantsIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  private static final String TENANT_ID_1 = "tenant1";
  private static final String TENANT_ID_2 = "tenant2";
  private static final String USERNAME_1 = "user1";
  private static final String USERNAME_2 = "user2";

  private static CamundaClient adminClient;

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(USERNAME_1, "password", List.of());

  @UserDefinition
  private static final TestUser USER_2 = new TestUser(USERNAME_2, "password", List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient client) {
    adminClient = client;

    // Create tenants and assign users
    createTenant(
        adminClient,
        TENANT_ID_1,
        TENANT_ID_1,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USERNAME_1);

    createTenant(
        adminClient,
        TENANT_ID_2,
        TENANT_ID_2,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USERNAME_2);
  }

  // ============ VARIABLE SCOPING TESTS ============

  @Test
  void shouldEnforceClusterScopeAlwaysReturnsGlobalVariable() {
    // given - create both global and tenant variable with same name
    final String variableName = "scopedVar_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "Tenant1Value")
        .send()
        .join();

    // when - access via .cluster from tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - .cluster ALWAYS returns global value, ignoring tenant scope
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo("GlobalValue");

    // when - access via .cluster from tenant2 (no tenant variable)
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - .cluster ALWAYS returns global value
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo("GlobalValue");
  }

  @Test
  void shouldEnforceTenantScopeAlwaysReturnsTenantSpecificVariable() {
    // given - create both global and tenant variable with same name
    final String variableName = "tenantOnly_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "Tenant1Value")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_2)
        .create(variableName, "Tenant2Value")
        .send()
        .join();

    // when - access via .tenant from tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - .tenant ONLY returns tenant-scoped value, never global
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo("Tenant1Value");

    // when - access via .tenant from tenant2
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - .tenant ONLY returns tenant-scoped value for tenant2
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo("Tenant2Value");
  }

  @Test
  void shouldEnforceEnvScopePrioritizesTenantOverGlobal() {
    // given - create global variable
    final String variableName = "envPriority_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    // when - access via .env from tenant1 (no tenant override yet)
    final EvaluateExpressionResponse responseBeforeOverride =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - .env returns global value when no tenant variable exists
    assertThat(responseBeforeOverride).isNotNull();
    assertThat(responseBeforeOverride.getResult()).isEqualTo("GlobalValue");

    // given - now create tenant-scoped override
    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "Tenant1Override")
        .send()
        .join();

    // when - access via .env from tenant1 (with tenant override)
    final EvaluateExpressionResponse responseAfterOverride =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - .env now returns tenant value (tenant takes priority over global)
    assertThat(responseAfterOverride).isNotNull();
    assertThat(responseAfterOverride.getResult()).isEqualTo("Tenant1Override");

    // when - access via .env from tenant2 (no tenant override for tenant2)
    final EvaluateExpressionResponse responseTenant2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - .env falls back to global value for tenant2
    assertThat(responseTenant2).isNotNull();
    assertThat(responseTenant2.getResult()).isEqualTo("GlobalValue");
  }

  @Test
  void shouldDemonstrateAllThreeScopesWithSameVariableName() {
    // given - create same variable name in global and two tenant scopes
    final String variableName = "multiScope_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GLOBAL")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "TENANT1")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_2)
        .create(variableName, "TENANT2")
        .send()
        .join();

    // when - evaluate all three scopes from tenant1 context
    final EvaluateExpressionResponse clusterResponse =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    final EvaluateExpressionResponse tenantResponse =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    final EvaluateExpressionResponse envResponse =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - each scope returns its specific value
    assertThat(clusterResponse.getResult())
        .as(".cluster always returns global")
        .isEqualTo("GLOBAL");
    assertThat(tenantResponse.getResult())
        .as(".tenant always returns tenant-specific")
        .isEqualTo("TENANT1");
    assertThat(envResponse.getResult())
        .as(".env prioritizes tenant over global")
        .isEqualTo("TENANT1");

    // when - evaluate all three scopes from tenant2 context
    final EvaluateExpressionResponse clusterResponse2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    final EvaluateExpressionResponse tenantResponse2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    final EvaluateExpressionResponse envResponse2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - tenant2 gets different tenant-specific values
    assertThat(clusterResponse2.getResult())
        .as(".cluster always returns global (same for all tenants)")
        .isEqualTo("GLOBAL");
    assertThat(tenantResponse2.getResult())
        .as(".tenant returns tenant2-specific value")
        .isEqualTo("TENANT2");
    assertThat(envResponse2.getResult())
        .as(".env prioritizes tenant2 value over global")
        .isEqualTo("TENANT2");
  }

  @Test
  void shouldShowEnvFallbackWhenOnlyGlobalExists() {
    // given - create only global variable (no tenant overrides)
    final String variableName = "globalOnly_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    // when - access via .env from tenant1 (no tenant variable)
    final EvaluateExpressionResponse envResponse1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - .env falls back to global when no tenant variable exists
    assertThat(envResponse1).isNotNull();
    assertThat(envResponse1.getResult()).isEqualTo("GlobalValue");

    // when - access via .cluster from tenant1
    final EvaluateExpressionResponse clusterResponse1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - .cluster returns global (as always)
    assertThat(clusterResponse1).isNotNull();
    assertThat(clusterResponse1.getResult()).isEqualTo("GlobalValue");

    // when - try to access via .tenant from tenant1
    // then - this should fail because .tenant ONLY looks at tenant scope
    final EvaluateExpressionResponse tenantResponse1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    assertThat(tenantResponse1).isNotNull();
    assertThat(tenantResponse1.getResult()).isNull();
  }

  @Test
  void shouldShowTenantScopeDoesNotFallbackToGlobal() {
    // given - create only global variable
    final String variableName = "tenantNoFallback_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    // when/then - .tenant does NOT fall back to global
    final EvaluateExpressionResponse firstResponse =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    assertThat(firstResponse).isNotNull();
    assertThat(firstResponse.getResult()).isNull();

    // given - now create tenant-scoped variable
    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "Tenant1Value")
        .send()
        .join();

    // when - access via .tenant again
    final EvaluateExpressionResponse secondResponse =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - now it works and returns tenant value
    assertThat(secondResponse).isNotNull();
    assertThat(secondResponse.getResult()).isEqualTo("Tenant1Value");
  }

  // ============ TENANT ISOLATION TESTS ============

  @Test
  void shouldIsolateTenantClusterVariablesBetweenTenants() {
    // given - create variables in different tenants with same name
    final String variableName = "isolatedVar_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "Tenant1Value")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_2)
        .create(variableName, "Tenant2Value")
        .send()
        .join();

    // when - evaluate for tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - should get tenant1 value
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo("Tenant1Value");

    // when - evaluate for tenant2
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.tenant." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - should get tenant2 value
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo("Tenant2Value");
  }

  @Test
  void shouldAccessGlobalVariableFromAnyTenant() {
    // given - create global variable
    final String variableName = "globalVar_" + UUID.randomUUID().toString().replace("-", "");
    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    // when - access from tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - should access global variable
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo("GlobalValue");

    // when - access from tenant2
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - should access same global variable
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo("GlobalValue");
  }

  @Test
  void shouldPrioritizeTenantVariableOverGlobalInEnvContext() {
    // given - create both global and tenant variable with same name
    final String variableName = "priorityVar_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(variableName, "GlobalValue")
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(variableName, "Tenant1Override")
        .send()
        .join();

    // when - access via env from tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - tenant value should override global
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo("Tenant1Override");

    // when - access via env from tenant2 (no tenant override)
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.env." + variableName)
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - should get global value
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo("GlobalValue");
  }

  // ============ COMPLEX MULTI-TENANT SCENARIOS ============

  @Test
  void shouldEvaluateComplexExpressionWithMixedGlobalAndTenantVariables() {
    // given - create global and tenant-specific variables
    final String globalTaxRate = "taxRate_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantDiscount = "discount_" + UUID.randomUUID().toString().replace("-", "");
    final String price = "price_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(globalTaxRate, 0.2) // 20% tax
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(tenantDiscount, 0.1) // 10% discount
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(price, 100.0)
        .send()
        .join();

    // when - calculate final price: (price - discount) * (1 + tax)
    final EvaluateExpressionResponse response =
        adminClient
            .newEvaluateExpressionCommand()
            .expression(
                "=(camunda.vars.tenant."
                    + price
                    + " * (1 - camunda.vars.tenant."
                    + tenantDiscount
                    + ")) * (1 + camunda.vars.cluster."
                    + globalTaxRate
                    + ")")
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - (100 * 0.9) * 1.2 = 108
    assertThat(response).isNotNull();
    assertThat(response.getResult()).isEqualTo(108);
  }

  @Test
  void shouldEvaluateConditionalExpressionBasedOnTenantConfiguration() {
    // given - different threshold for each tenant
    final String threshold = "threshold_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(threshold, 100)
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_2)
        .create(threshold, 200)
        .send()
        .join();

    // when - evaluate same expression for tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression(
                "=if 150 > camunda.vars.tenant." + threshold + " then \"high\" else \"low\"")
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - 150 > 100 = high
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo("high");

    // when - evaluate same expression for tenant2
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression(
                "=if 150 > camunda.vars.tenant." + threshold + " then \"high\" else \"low\"")
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - 150 > 200 = low
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo("low");
  }

  @Test
  void shouldHandleTenantSpecificObjectsAndGlobalReferences() {
    // given - global reference data and tenant-specific configuration
    final String globalCurrencies = "currencies_" + UUID.randomUUID().toString().replace("-", "");
    final String tenantConfig = "config_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(globalCurrencies, Map.of("EUR", 0.90))
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(globalCurrencies, Map.of("EUR", 0.85))
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(tenantConfig, Map.of("currency", "EUR", "amount", 100))
        .send()
        .join();

    // when - calculate amount in USD using tenant currency preference
    final EvaluateExpressionResponse response =
        adminClient
            .newEvaluateExpressionCommand()
            .expression(
                "=camunda.vars.tenant."
                    + tenantConfig
                    + ".amount / camunda.vars.env."
                    + globalCurrencies
                    + ".EUR")
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - 100 / 0.85 ≈ 117.647
    assertThat(response).isNotNull();
    assertThat((Double) response.getResult())
        .isCloseTo(117.647, org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  void shouldEvaluateTenantSpecificListOperations() {
    // given - tenant-specific lists
    final String allowedRegions = "regions_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(allowedRegions, List.of("US", "EU", "APAC"))
        .send()
        .join();

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_2)
        .create(allowedRegions, List.of("US", "EU"))
        .send()
        .join();

    // when - check if region is in allowed list for tenant1
    final EvaluateExpressionResponse response1 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=list contains(camunda.vars.tenant." + allowedRegions + ", \"APAC\")")
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then - APAC is in tenant1's list
    assertThat(response1).isNotNull();
    assertThat(response1.getResult()).isEqualTo(true);

    // when - check if region is in allowed list for tenant2
    final EvaluateExpressionResponse response2 =
        adminClient
            .newEvaluateExpressionCommand()
            .expression("=list contains(camunda.vars.tenant." + allowedRegions + ", \"APAC\")")
            .tenantId(TENANT_ID_2)
            .send()
            .join();

    // then - APAC is not in tenant2's list
    assertThat(response2).isNotNull();
    assertThat(response2.getResult()).isEqualTo(false);
  }

  @Test
  void shouldCombineStaticValuesWithTenantVariablesInComplexExpression() {
    // given - tenant-specific configuration
    final String multiplier = "multiplier_" + UUID.randomUUID().toString().replace("-", "");

    adminClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID_1)
        .create(multiplier, 2.5)
        .send()
        .join();

    // when - combine static value with tenant variable
    final EvaluateExpressionResponse response =
        adminClient
            .newEvaluateExpressionCommand()
            .expression(
                "={\"baseValue\": 10, \"multiplied\": 10 * camunda.vars.tenant."
                    + multiplier
                    + ", \"label\": \"Tenant "
                    + TENANT_ID_1
                    + "\"}")
            .tenantId(TENANT_ID_1)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResult())
        .isEqualTo(Map.of("baseValue", 10, "multiplied", 25, "label", "Tenant " + TENANT_ID_1));
  }
}
