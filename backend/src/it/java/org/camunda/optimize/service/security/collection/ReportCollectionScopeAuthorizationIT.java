/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.collection.CollectionScopeService.UNAUTHORIZED_TENANT_MASK;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public class ReportCollectionScopeAuthorizationIT extends AbstractIT {

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private ImmutableMap<Integer, DefinitionType> resourceTypeToDefinitionType =
    ImmutableMap.of(RESOURCE_TYPE_PROCESS_DEFINITION, PROCESS,
                    RESOURCE_TYPE_DECISION_DEFINITION, DECISION
    );

  private static Stream<Integer> definitionTypes() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  private static Stream<List<Integer>> definitionTypePairs() {
    return Stream.of(
      asList(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION),
      asList(RESOURCE_TYPE_DECISION_DEFINITION, RESOURCE_TYPE_PROCESS_DEFINITION)
    );
  }

  @ParameterizedTest(name = "get scope for collection where user is authorized for key of type {0}")
  @MethodSource("definitionTypes")
  public void getScopesForAuthorizedCollection_keySpecific(final int definitionType) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, "KEY_1", definitionType);
    createScopeForCollection(collectionId, "KEY_2", definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit("KEY_1", definitionType);
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryRestDto::getDefinitionKey)
      .containsExactly("KEY_1");
  }

  @ParameterizedTest(name = "get scope for collection where user is authorized for key of one type but not the other")
  @MethodSource("definitionTypePairs")
  public void getScopesForAuthorizedCollection_typeSpecific(final List<Integer> typePair) {
    // given
    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, "KEY_1", typePair.get(0));
    createScopeForCollection(collectionId, "KEY_2", typePair.get(1));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(typePair.get(0));
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryRestDto::getDefinitionKey)
      .containsExactly("KEY_1");
  }

  @Test
  public void getScopesForAuthorizedCollection_groupSpecific() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, "KEY_1", RESOURCE_TYPE_PROCESS_DEFINITION);
    createScopeForCollection(collectionId, "KEY_2", RESOURCE_TYPE_PROCESS_DEFINITION);


    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantSingleResourceAuthorizationsForGroup(GROUP_ID, "KEY_1", RESOURCE_TYPE_PROCESS_DEFINITION);
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryRestDto::getDefinitionKey)
      .containsExactly("KEY_1");
  }

  @ParameterizedTest(name = "get scope for collection where user is authorized for at least one tenant and type {0}")
  @MethodSource("definitionTypes")
  public void getOnlyScopesWhereUserIsAuthorizedToAtLeastOneTenant(final int definitionType) {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);

    final String unauthorizedTenant1 = "unauthorizedTenant1";
    engineIntegrationExtension.createTenant(unauthorizedTenant1);
    final String unauthorizedTenant2 = "unauthorizedTenant2";
    engineIntegrationExtension.createTenant(unauthorizedTenant2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, authorizedTenant, RESOURCE_TYPE_TENANT);
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionType);

    // shared definitions (any tenant is possible)
    deployAndImportDefinition(definitionType, "KEY_1", null);
    deployAndImportDefinition(definitionType, "KEY_2", null);
    deployAndImportDefinition(definitionType, "KEY_3", null);
    deployAndImportDefinition(definitionType, "KEY_3", null);
    deployAndImportDefinition(definitionType, "KEY_4", null);
    // tenant specific definitions
    deployAndImportDefinition(definitionType, "KEY_5", unauthorizedTenant1);
    deployAndImportDefinition(definitionType, "KEY_6", authorizedTenant);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(collectionId, "KEY_1", asList(authorizedTenant, unauthorizedTenant1), definitionType);
    createScopeWithTenants(collectionId, "KEY_2", asList(unauthorizedTenant1, unauthorizedTenant2), definitionType);
    createScopeWithTenants(collectionId, "KEY_3", asList(unauthorizedTenant1, null), definitionType);
    createScopeWithTenants(
      collectionId,
      "KEY_4",
      asList(unauthorizedTenant1, unauthorizedTenant2, authorizedTenant),
      definitionType
    );
    createScopeWithTenants(collectionId, "KEY_5", asList(unauthorizedTenant1), definitionType);
    createScopeWithTenants(collectionId, "KEY_6", asList(authorizedTenant), definitionType);
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryRestDto::getDefinitionKey)
      .containsExactlyInAnyOrder("KEY_1", "KEY_3", "KEY_4", "KEY_6");
  }

  @ParameterizedTest(name = "unauthorized tenants get masked for type {0}")
  @MethodSource("definitionTypes")
  public void unauthorizedTenantsAreMasked(final int definitionType) {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);

    final String unauthorizedTenant1 = "unauthorizedTenant1";
    engineIntegrationExtension.createTenant(unauthorizedTenant1);
    final String unauthorizedTenant2 = "unauthorizedTenant2";
    engineIntegrationExtension.createTenant(unauthorizedTenant2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, authorizedTenant, RESOURCE_TYPE_TENANT);
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionType);

    deployAndImportDefinition(definitionType, "KEY_1", authorizedTenant);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(authorizedTenant, null, unauthorizedTenant1, unauthorizedTenant2),
      definitionType
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);

    // then
    assertThat(scopeEntries)
      .hasSize(1)
      .flatExtracting(CollectionScopeEntryRestDto::getTenants)
      .containsExactlyInAnyOrder(
        TENANT_NOT_DEFINED,
        new TenantDto(authorizedTenant, authorizedTenant, DEFAULT_ENGINE_ALIAS),
        UNAUTHORIZED_TENANT_MASK,
        UNAUTHORIZED_TENANT_MASK
      );
  }

  @ParameterizedTest(name = "remove tenant with masked tenants does not distort scope for type {0}")
  @MethodSource("definitionTypes")
  public void removeTenantWithMaskedTenantsDoesNotDistortScope(final int definitionType) {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);

    final String unauthorizedTenant1 = "unauthorizedTenant1";
    engineIntegrationExtension.createTenant(unauthorizedTenant1);
    final String unauthorizedTenant2 = "unauthorizedTenant2";
    engineIntegrationExtension.createTenant(unauthorizedTenant2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, authorizedTenant, RESOURCE_TYPE_TENANT);
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionType);

    deployAndImportDefinition(definitionType, "KEY_1", authorizedTenant);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(authorizedTenant, null, unauthorizedTenant1, unauthorizedTenant2),
      definitionType
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.MANAGER), collectionId);

    List<CollectionScopeEntryRestDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);
    assertThat(scopeEntries).hasSize(1)
      .flatExtracting(CollectionScopeEntryRestDto::getTenants)
      .contains(UNAUTHORIZED_TENANT_MASK);
    final CollectionScopeEntryRestDto scopeEntry = scopeEntries.get(0);
    List<String> oneTenantRemoved = scopeEntry
      .getTenants()
      .stream()
      .map(TenantDto::getId)
      .filter(t -> !authorizedTenant.equals(t))
      .collect(toList());

    // when update the result with masked tenants
    collectionClient.updateCollectionScope(collectionId, scopeEntry, oneTenantRemoved);
    scopeEntries = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scopeEntries)
      .hasSize(1)
      .flatExtracting(CollectionScopeEntryRestDto::getTenants)
      .extracting(TenantDto::getId)
      .containsExactlyInAnyOrder(null, unauthorizedTenant1, unauthorizedTenant2);
  }

  @ParameterizedTest(name = "add tenant with masked tenants does not distort scope for type {0}")
  @MethodSource("definitionTypes")
  public void addTenantWithMaskedTenantsDoesNotDistortScope(final int definitionType) {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);

    final String unauthorizedTenant1 = "unauthorizedTenant1";
    engineIntegrationExtension.createTenant(unauthorizedTenant1);
    final String unauthorizedTenant2 = "unauthorizedTenant2";
    engineIntegrationExtension.createTenant(unauthorizedTenant2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, authorizedTenant, RESOURCE_TYPE_TENANT);
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionType);

    deployAndImportDefinition(definitionType, "KEY_1", authorizedTenant);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(unauthorizedTenant1, null, unauthorizedTenant2),
      definitionType
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.MANAGER), collectionId);

    List<CollectionScopeEntryRestDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);
    assertThat(scopeEntries).hasSize(1)
      .flatExtracting(CollectionScopeEntryRestDto::getTenants)
      .contains(UNAUTHORIZED_TENANT_MASK);
    final CollectionScopeEntryRestDto scopeEntry = scopeEntries.get(0);
    List<String> oneTenantAdded = scopeEntry
      .getTenants()
      .stream()
      .map(TenantDto::getId)
      .collect(toList());
    oneTenantAdded.add(authorizedTenant);

    // when update the result with masked tenants
    collectionClient.updateCollectionScope(collectionId, scopeEntry, oneTenantAdded);
    scopeEntries = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scopeEntries)
      .hasSize(1)
      .flatExtracting(CollectionScopeEntryRestDto::getTenants)
      .extracting(TenantDto::getId)
      .containsExactlyInAnyOrder(authorizedTenant, null, unauthorizedTenant1, unauthorizedTenant2);
  }

  private void deployAndImportDefinition(int definitionResourceType, final String definitionKey,
                                         final String tenantId) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deploySimpleProcessDefinition(definitionKey, tenantId);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deploySimpleDecisionDefinition(definitionKey, tenantId);
        break;
      default:
        throw new IllegalStateException("Uncovered definitionResourceType: " + definitionResourceType);
    }

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void deploySimpleProcessDefinition(final String processId, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .endEvent()
      .done();
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private void deploySimpleDecisionDefinition(final String decisionKey, final String tenantId) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    engineIntegrationExtension.deployDecisionDefinition(modelInstance, tenantId);
  }

  private void createScopeForCollection(final String collectionId, final String definitionKey, final int resourceType) {
    collectionClient.createScopeForCollection(
      collectionId,
      definitionKey,
      resourceTypeToDefinitionType.get(resourceType)
    );
  }

  private void createScopeWithTenants(final String collectionId, String definitionKey,
                                      List<String> tenants, final int resourceType) {
    collectionClient.createScopeWithTenants(
      collectionId,
      definitionKey,
      tenants,
      resourceTypeToDefinitionType.get(resourceType)
    );
  }

  private void addRoleToCollectionAsDefaultUser(final CollectionRoleDto roleDto,
                                                final String collectionId) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class ScopeScenario {

    String collectionIdToAddReportTo;
    String definitionKey;
    List<String> tenants;
  }

}
