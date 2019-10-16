/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiEngineReportAuthorizationIT extends AbstractMultiEngineIT {

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule defaultEngineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EngineIntegrationExtensionRule secondaryEngineIntegrationExtensionRule = new EngineIntegrationExtensionRule("anotherEngine");
  @RegisterExtension
  @Order(4)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(defaultEngineIntegrationExtensionRule);
  private AuthorizationClient secondAuthorizationClient = new AuthorizationClient(
    secondaryEngineIntegrationExtensionRule);

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizedForMultiEngineBasedReportsWhenAuthorizedByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto defaultEngineKeyReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType), definitionResourceType
    );
    final SingleReportDataDto secondEngineKeyReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType), definitionResourceType
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    Response responseSecondEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
    assertThat(responseSecondEngineKey.getStatus(), is(200));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizedForSingleEngineReportWhenAuthorizedByParticularEngineAndOtherEngineIsDown(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto defaultEngineKeyReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType), definitionResourceType
    );

    // when
    addNonExistingSecondEngineToConfiguration();

    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForSingleEngineReportWhenAuthorizedByOneEngineAndOtherEngineIsDown(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto secondEngineKeyReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType), definitionResourceType
    );

    // when
    addNonExistingSecondEngineToConfiguration();

    Response responseSecondEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizedForSingleEngineReportWhenAuthorizedByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto defaultEngineKeyReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType), definitionResourceType
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForSingleEngineReportWhenNotAuthorizedByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto secondEngineKeyReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType), definitionResourceType
    );

    // when
    Response responseSecondEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizedForMultiEngineDefaultTenantsWhenAuthorizedByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForMultiEngineDefaultTenantsWhenAuthorizedToAccessOptimizeByOnlyOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondaryEngineIntegrationExtensionRule.addUser(KERMIT_USER, KERMIT_USER);

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForMultiEngineDefaultTenantsWhenAuthorizedForKeyByOnlyOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizedForTenantAndKeyByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineIntegrationExtensionRule.createTenant(tenantId1);
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId1, RESOURCE_TYPE_TENANT);
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtensionRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    final SingleReportDataDto defaultEngineTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId1)
    );
    final SingleReportDataDto secondEngineTenantReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineTenantReport)
      .execute();

    Response responseSecondEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForTenantAlthoughAuthorizedForKeyByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineIntegrationExtensionRule.createTenant(tenantId1);
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId1, RESOURCE_TYPE_TENANT);
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtensionRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    final SingleReportDataDto secondEngineTenantReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId2)
    );

    // when
    Response responseSecondEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineTenantReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForKeyAlthoughAuthorizedForTenantByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultEngineIntegrationExtensionRule.createTenant(tenantId1);
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId1, RESOURCE_TYPE_TENANT);
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtensionRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId2, RESOURCE_TYPE_TENANT);

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    final SingleReportDataDto secondEngineTenantReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId2)
    );

    // when
    Response responseSecondEngineKey = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineTenantReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  private String getDefinitionKeyDefaultEngine(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY_1 : DECISION_KEY_1;
  }

  private String getDefinitionKeySecondEngine(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY_2 : DECISION_KEY_2;
  }

  private SingleReportDataDto constructReportData(String key, int resourceType) {
    return constructReportData(key, resourceType, Collections.emptyList());
  }

  private SingleReportDataDto constructReportData(String key, int resourceType, List<String> tenantIds) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        ProcessReportDataDto processReportDataDto = ProcessReportDataBuilder.createReportData()
          .setProcessDefinitionKey(key)
          .setProcessDefinitionVersion("1")
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        processReportDataDto.setTenantIds(tenantIds);
        return processReportDataDto;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(key)
          .setDecisionDefinitionVersion("1")
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.setTenantIds(tenantIds);
        return decisionReportDataDto;
    }
  }
}
