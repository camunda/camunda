/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.util.configuration.DefaultTenant;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class MultiEngineReportAuthorizationIT extends AbstractMultiEngineIT {

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  public AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(defaultEngineRule);
  public AuthorizationClient secondAuthorizationClient = new AuthorizationClient(secondEngineRule);

  @Test
  @Parameters(method = "definitionType")
  public void authorizedForDifferentEngineBasedReportsWhenAuthorizedByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto defaultEngineKeyReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType), definitionResourceType
    );
    final SingleReportDataDto secondEngineKeyReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType), definitionResourceType
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    Response responseSecondEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
    assertThat(responseSecondEngineKey.getStatus(), is(200));
  }

  @Test
  @Parameters(method = "definitionType")
  public void authorizedForEngineBasedReportWhenAuthorizedByOneEngineAndOtherEngineIsDown(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto defaultEngineKeyReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType), definitionResourceType
    );

    // when
    addNonExistingSecondEngineToConfiguration();

    Response responseDefaultEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
  }

  @Test
  @Parameters(method = "definitionType")
  public void unauthorizedForEngineBasedReportWhenAuthorizedByOneEngineAndOtherEngineIsDown(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto secondEngineKeyReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType), definitionResourceType
    );

    // when
    addNonExistingSecondEngineToConfiguration();

    Response responseSecondEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void authorizedForEngineBasedReportWhenAuthorizedByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto defaultEngineKeyReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType), definitionResourceType
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
  }

  @Test
  @Parameters(method = "definitionType")
  public void unauthorizedForEngineBasedReportWhenNotAuthorizedByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto secondEngineKeyReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType), definitionResourceType
    );

    // when
    Response responseSecondEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void authorizedForDifferentEngineDefaultTenantsWhenAuthorizedByAllEngines(int definitionResourceType) {
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

    embeddedOptimizeRule.reloadConfiguration();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(200));
  }

  @Test
  @Parameters(method = "definitionType")
  public void unauthorizedForDifferentEngineDefaultTenantsWhenAuthorizedByOnlyOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondEngineRule.addUser(KERMIT_USER, KERMIT_USER);

    embeddedOptimizeRule.reloadConfiguration();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, null);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      ImmutableList.of(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(403));
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
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
          key, "1"
        );
        decisionReportDataDto.setTenantIds(tenantIds);
        return decisionReportDataDto;
    }
  }
}
