/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiEngineReportAuthorizationIT extends AbstractMultiEngineIT {

  private AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(engineIntegrationExtension);
  private AuthorizationClient secondAuthorizationClient = new AuthorizationClient(
    secondaryEngineIntegrationExtension);

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
    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    Response responseSecondEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(responseSecondEngineKey.getStatus(), is(Response.Status.OK.getStatusCode()));
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

    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.OK.getStatusCode()));
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

    Response responseSecondEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
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
    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineKeyReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.OK.getStatusCode()));
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
    Response responseSecondEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineKeyReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
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

    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.OK.getStatusCode()));
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
    secondaryEngineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);

    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
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

    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    final SingleReportDataDto multiTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId1, tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(multiTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void authorizedForTenantAndKeyByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.createTenant(tenantId1);
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId1, RESOURCE_TYPE_TENANT);
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtension.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    final SingleReportDataDto defaultEngineTenantReport = constructReportData(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId1)
    );
    final SingleReportDataDto secondEngineTenantReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId2)
    );

    // when
    Response responseDefaultEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(defaultEngineTenantReport)
      .execute();

    Response responseSecondEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineTenantReport)
      .execute();

    // then
    assertThat(responseDefaultEngineKey.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(responseSecondEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForTenantAlthoughAuthorizedForKeyByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.createTenant(tenantId1);
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId1, RESOURCE_TYPE_TENANT);
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtension.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    final SingleReportDataDto secondEngineTenantReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId2)
    );

    // when
    Response responseSecondEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineTenantReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void unauthorizedForKeyAlthoughAuthorizedForTenantByParticularEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.createTenant(tenantId1);
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId1, RESOURCE_TYPE_TENANT);
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtension.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationForKermit(tenantId2, RESOURCE_TYPE_TENANT);

    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    final SingleReportDataDto secondEngineTenantReport = constructReportData(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType,
      Lists.newArrayList(tenantId2)
    );

    // when
    Response responseSecondEngineKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(secondEngineTenantReport)
      .execute();

    // then
    assertThat(responseSecondEngineKey.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
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
        ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder.createReportData()
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

  private static final Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }
}
