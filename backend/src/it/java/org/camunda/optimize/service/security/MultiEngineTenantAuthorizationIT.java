/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.util.configuration.DefaultTenant;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@RunWith(JUnitParamsRunner.class)
public class MultiEngineTenantAuthorizationIT extends AbstractMultiEngineIT {

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  public AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(defaultEngineRule);
  public AuthorizationClient secondAuthorizationClient = new AuthorizationClient(secondEngineRule);

  @Test
  public void getAllStoredTenantsGrantedAccessToByAllEngines() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultEngineRule.createTenant(tenantId1);
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    final String tenantId2 = "tenant2";
    secondEngineRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(3));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1, tenantId2)
    );
  }

  @Test
  public void getAllStoredTenantsGrantedAccessToByOneEngine() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultEngineRule.createTenant(tenantId1);
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    secondEngineRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(2));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId2)
    );
  }

  @Test
  public void getAllDefaultTenantsForAllAuthorizedEngines() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    embeddedOptimizeRule.reloadConfiguration();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(3));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1, tenantId2)
    );
  }

  @Test
  public void getDefaultTenantFromAuthorizedEnginesOnly() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondEngineRule.addUser(KERMIT_USER, KERMIT_USER);

    embeddedOptimizeRule.reloadConfiguration();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeRule.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(2));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1)
    );
  }

  @Test
  @Parameters(method = "definitionType")
  public void getDefinitionVersionsWithTenantsForSameKeyAndDefaultTenantPerEngineByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    embeddedOptimizeRule.reloadConfiguration();

    deployStartAndImportDefinitionsWithSameKeyOnAllEngines(definitionResourceType);

    //when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsKermit(definitionResourceType);

    //then
    Assert.assertThat(definitions.size(), is(1));
    Assert.assertThat(definitions.get(0).getVersions().size(), is(2));
    Assert.assertThat(definitions.get(0).getVersions().get(0).getTenants().size(), is(2));
    Assert.assertThat(
      definitions.get(0).getVersions().get(0)
        .getTenants().stream().map(TenantRestDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(tenantId1, tenantId2)
    );
  }

  @Test
  @Parameters(method = "definitionType")
  public void getDefinitionVersionsWithTenantsForSameKeyAndDefaultTenantPerEngineByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    embeddedOptimizeRule.reloadConfiguration();

    deployStartAndImportDefinitionsWithSameKeyOnAllEngines(definitionResourceType);

    //when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsKermit(definitionResourceType);

    //then
    Assert.assertThat(definitions.size(), is(1));
    Assert.assertThat(definitions.get(0).getVersions().size(), is(2));
    Assert.assertThat(definitions.get(0).getVersions().get(0).getTenants().size(), is(1));
    Assert.assertThat(definitions.get(0).getVersions().get(0).getTenants().get(0).getId(), is(tenantId1));
  }

  private void deployStartAndImportDefinitionsWithSameKeyOnAllEngines(final int definitionResourceType) {
    deployStartAndImportDefinitionsWithSameKeyOnAllEngines(definitionResourceType, null);
  }

  private void deployStartAndImportDefinitionsWithSameKeyOnAllEngines(final int definitionResourceType,
                                                                      final String tenantId) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deployAndStartProcessOnDefaultEngine(PROCESS_KEY_1, tenantId);
        deployAndStartProcessOnSecondEngine(PROCESS_KEY_1, tenantId);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        defaultEngineRule.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), tenantId);
        secondEngineRule.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), tenantId);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resource type: " + definitionResourceType);
    }
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
  }

  private List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenantsAsKermit(
    final int definitionResourceType) {
    final List<DefinitionVersionsWithTenantsRestDto> definitions;
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        definitions = embeddedOptimizeRule
          .getRequestExecutor()
          .withUserAuthentication(KERMIT_USER, KERMIT_USER)
          .buildGetProcessDefinitionVersionsWithTenants()
          .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        definitions = embeddedOptimizeRule
          .getRequestExecutor()
          .withUserAuthentication(KERMIT_USER, KERMIT_USER)
          .buildGetDecisionDefinitionVersionsWithTenants()
          .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resource type: " + definitionResourceType);
    }
    return definitions;
  }

}
