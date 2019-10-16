/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class MultiEngineTenantAuthorizationIT extends AbstractMultiEngineIT {

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

  @Test
  public void getAllStoredTenantsGrantedAccessToByAllEngines() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultEngineIntegrationExtensionRule.createTenant(tenantId1);
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtensionRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtensionRule.getTenantService().getTenantsForUser(KERMIT_USER);

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
    defaultEngineIntegrationExtensionRule.createTenant(tenantId1);
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtensionRule.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtensionRule.getTenantService().getTenantsForUser(KERMIT_USER);

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

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtensionRule.getTenantService().getTenantsForUser(KERMIT_USER);

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
    secondaryEngineIntegrationExtensionRule.addUser(KERMIT_USER, KERMIT_USER);

    embeddedOptimizeExtensionRule.reloadConfiguration();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtensionRule.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(2));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1)
    );
  }

  @ParameterizedTest
  @MethodSource("definitionType")
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

    embeddedOptimizeExtensionRule.reloadConfiguration();

    deployStartAndImportDefinitionsWithSameKeyOnAllEngines(definitionResourceType);

    //when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsKermit(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getVersions().size(), is(1));
    assertThat(definitions.get(0).getVersions().get(0).getTenants().size(), is(2));
    assertThat(
      definitions.get(0).getVersions().get(0)
        .getTenants().stream().map(TenantRestDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(tenantId1, tenantId2)
    );
  }

  @ParameterizedTest
  @MethodSource("definitionType")
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

    embeddedOptimizeExtensionRule.reloadConfiguration();

    deployStartAndImportDefinitionsWithSameKeyOnAllEngines(definitionResourceType);

    //when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsKermit(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getVersions().size(), is(1));
    assertThat(definitions.get(0).getVersions().get(0).getTenants().size(), is(1));
    assertThat(definitions.get(0).getVersions().get(0).getTenants().get(0).getId(), is(tenantId1));
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
        defaultEngineIntegrationExtensionRule.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), tenantId);
        secondaryEngineIntegrationExtensionRule.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), tenantId);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported resource type: " + definitionResourceType);
    }
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
  }

  private List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenantsAsKermit(
    final int definitionResourceType) {
    final List<DefinitionVersionsWithTenantsRestDto> definitions;
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        definitions = embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .withUserAuthentication(KERMIT_USER, KERMIT_USER)
          .buildGetProcessDefinitionVersionsWithTenants()
          .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        definitions = embeddedOptimizeExtensionRule
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

  private static final Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

}
