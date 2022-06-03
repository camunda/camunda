/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryResponseDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.collection.CollectionScopeService.SCOPE_NOT_AUTHORIZED_MESSAGE;
import static org.camunda.optimize.service.collection.CollectionScopeService.UNAUTHORIZED_TENANT_MASK;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class CollectionScopeAuthorizationIT extends AbstractIT {

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private ImmutableMap<Integer, DefinitionType> resourceTypeToDefinitionType =
    ImmutableMap.of(RESOURCE_TYPE_PROCESS_DEFINITION, PROCESS,
                    RESOURCE_TYPE_DECISION_DEFINITION, DECISION
    );

  private static Stream<Integer> definitionResourceTypes() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  private static Stream<List<Integer>> definitionTypePairs() {
    return Stream.of(
      asList(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION),
      asList(RESOURCE_TYPE_DECISION_DEFINITION, RESOURCE_TYPE_PROCESS_DEFINITION)
    );
  }

  @ParameterizedTest(name = "get scope for collection where user is authorized for key of type {0}")
  @MethodSource("definitionResourceTypes")
  public void getScopesForAuthorizedCollection_keySpecific(final int definitionType) {
    // given
    deployAndImportDefinition(definitionType, "KEY_1", null);
    deployAndImportDefinition(definitionType, "KEY_2", null);
    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, "KEY_1", definitionType);
    createScopeForCollection(collectionId, "KEY_2", definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit("KEY_1", definitionType);
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId);

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<List<CollectionScopeEntryResponseDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionKey)
      .containsExactly("KEY_1");
  }

  @Test
  public void getScopesForCollection_keySpecific_eventBased() {
    // given
    final String key1 = "eventBasedKey1";
    final String key2 = "eventBasedKey2";

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key1);
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key2);

    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, key1, RESOURCE_TYPE_PROCESS_DEFINITION);
    createScopeForCollection(collectionId, key2, RESOURCE_TYPE_PROCESS_DEFINITION);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId);

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .execute(new TypeReference<List<CollectionScopeEntryResponseDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionKey)
      .containsExactlyInAnyOrder(key1, key2);
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getTenants)
      .containsOnly(Lists.newArrayList(TENANT_NOT_DEFINED));
  }

  @Test
  public void getScopesForCollection_keySpecific_excludeUnauthorizedEventBased() {
    // given
    final String key1 = "eventBasedKey1";
    final String key2 = "eventBasedKey2";

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      key1, "authorizedProcess", "1", ImmutableList.of(new UserDto(KERMIT_USER), new UserDto(DEFAULT_USERNAME))
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key2);

    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, key1, RESOURCE_TYPE_PROCESS_DEFINITION);
    createScopeForCollection(collectionId, key2, RESOURCE_TYPE_PROCESS_DEFINITION);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addRoleToCollectionAsDefaultUser(
      new CollectionRoleRequestDto(new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER),
      collectionId
    );

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<List<CollectionScopeEntryResponseDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionKey)
      .containsExactly(key1);
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getTenants)
      .containsOnly(Lists.newArrayList(TENANT_NOT_DEFINED));
  }

  @ParameterizedTest(name = "get scope for collection where user is authorized for key of one type but not the other")
  @MethodSource("definitionTypePairs")
  public void getScopesForAuthorizedCollection_typeSpecific(final List<Integer> typePair) {
    // given
    deployAndImportDefinition(typePair.get(0), "KEY_1", null);
    deployAndImportDefinition(typePair.get(1), "KEY_2", null);
    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, "KEY_1", typePair.get(0));
    createScopeForCollection(collectionId, "KEY_2", typePair.get(1));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(typePair.get(0));
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId);

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<List<CollectionScopeEntryResponseDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionKey)
      .containsExactly("KEY_1");
  }

  @Test
  public void getScopesForAuthorizedCollection_groupSpecific() {
    // given
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram("KEY_1"), null);
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram("KEY_2"), null);
    final String collectionId = collectionClient.createNewCollection();
    createScopeForCollection(collectionId, "KEY_1", RESOURCE_TYPE_PROCESS_DEFINITION);
    createScopeForCollection(collectionId, "KEY_2", RESOURCE_TYPE_PROCESS_DEFINITION);

    importAllEngineEntitiesFromScratch();

    authorizationClient.addKermitUserWithoutAuthorizations();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();
    authorizationClient.grantSingleResourceAuthorizationsForGroup(GROUP_ID, "KEY_1", RESOURCE_TYPE_PROCESS_DEFINITION);
    addRoleToCollectionAsDefaultUser(
      new CollectionRoleRequestDto(new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER),
      collectionId
    );

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionKey)
      .containsExactly("KEY_1");
  }

  @ParameterizedTest(name = "get scope for collection where user is authorized for at least one tenant and type {0}")
  @MethodSource("definitionResourceTypes")
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
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId);

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);

    // then
    assertThat(scopeEntries)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionKey)
      .containsExactlyInAnyOrder("KEY_1", "KEY_3", "KEY_4", "KEY_6");
  }

  @ParameterizedTest(name = "unauthorized tenants get masked for type {0}")
  @MethodSource("definitionResourceTypes")
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

    deployAndImportDefinition(definitionType, "KEY_1", null);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(authorizedTenant, null, unauthorizedTenant1, unauthorizedTenant2),
      definitionType
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId);

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);

    // then
    assertThat(scopeEntries)
      .hasSize(1)
      .flatExtracting(CollectionScopeEntryResponseDto::getTenants)
      .containsExactlyInAnyOrder(
        TENANT_NOT_DEFINED,
        new TenantDto(authorizedTenant, authorizedTenant, DEFAULT_ENGINE_ALIAS),
        UNAUTHORIZED_TENANT_MASK,
        UNAUTHORIZED_TENANT_MASK
      );
  }

  @ParameterizedTest(name = "remove tenant with masked tenants does not distort scope for type {0}")
  @MethodSource("definitionResourceTypes")
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

    deployAndImportDefinition(definitionType, "KEY_1", null);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(authorizedTenant, null, unauthorizedTenant1, unauthorizedTenant2),
      definitionType
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);
    assertThat(scopeEntries).hasSize(1)
      .flatExtracting(CollectionScopeEntryResponseDto::getTenants)
      .contains(UNAUTHORIZED_TENANT_MASK);
    final CollectionScopeEntryResponseDto scopeEntry = scopeEntries.get(0);
    List<String> oneTenantRemoved = scopeEntry
      .getTenants()
      .stream()
      .map(TenantDto::getId)
      .filter(t -> !authorizedTenant.equals(t))
      .collect(toList());

    // when update the result with masked tenants
    collectionClient.updateCollectionScopeAsKermit(collectionId, scopeEntry.getId(), oneTenantRemoved);
    scopeEntries = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scopeEntries)
      .hasSize(1)
      .flatExtracting(CollectionScopeEntryResponseDto::getTenants)
      .extracting(TenantDto::getId)
      .containsExactlyInAnyOrder(null, unauthorizedTenant1, unauthorizedTenant2);
  }

  @Test
  public void forceUpdateScopeWorksAlsoWithMaskedTenants() {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);

    final String unauthorizedTenant1 = "unauthorizedTenant1";
    engineIntegrationExtension.createTenant(unauthorizedTenant1);
    final String unauthorizedTenant2 = "unauthorizedTenant2";
    engineIntegrationExtension.createTenant(unauthorizedTenant2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, authorizedTenant, RESOURCE_TYPE_TENANT);
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    deployAndImportDefinition(RESOURCE_TYPE_PROCESS_DEFINITION, "KEY_1", null);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(authorizedTenant, DEFAULT_TENANT, unauthorizedTenant1, unauthorizedTenant2),
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);
    reportClient.createSingleReport(collectionId, PROCESS, "KEY_1", newArrayList(authorizedTenant, DEFAULT_TENANT));

    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);
    assertThat(scopeEntries).hasSize(1)
      .flatExtracting(CollectionScopeEntryResponseDto::getTenants)
      .contains(UNAUTHORIZED_TENANT_MASK);
    final CollectionScopeEntryResponseDto scopeEntry = scopeEntries.get(0);
    List<String> oneTenantRemoved = scopeEntry
      .getTenants()
      .stream()
      .map(TenantDto::getId)
      .filter(t -> !authorizedTenant.equals(t))
      .collect(toList());
    assertThat(collectionClient.getReportsForCollection(collectionId)).isNotEmpty();


    // when update the result with masked tenants
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        scopeEntry.getId(),
        new CollectionScopeEntryUpdateDto(oneTenantRemoved),
        true
      )
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .hasSize(1)
      .first()
      .extracting(r -> (SingleProcessReportDefinitionRequestDto) r)
      .extracting(SingleProcessReportDefinitionRequestDto::getData)
      .extracting(ProcessReportDataDto::getTenantIds)
      .asList()
      .contains(DEFAULT_TENANT);
  }

  @ParameterizedTest(name = "add tenant with masked tenants does not distort scope for type {0}")
  @MethodSource("definitionResourceTypes")
  public void addTenantWithMaskedTenantsDoesNotDistortScope(final int definitionResourceType) {
    // given
    final String authorizedTenant = "authorizedTenant";
    engineIntegrationExtension.createTenant(authorizedTenant);

    final String unauthorizedTenant1 = "unauthorizedTenant1";
    engineIntegrationExtension.createTenant(unauthorizedTenant1);
    final String unauthorizedTenant2 = "unauthorizedTenant2";
    engineIntegrationExtension.createTenant(unauthorizedTenant2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, authorizedTenant, RESOURCE_TYPE_TENANT);
    authorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    deployAndImportDefinition(definitionResourceType, "KEY_1", null);

    final String collectionId = collectionClient.createNewCollection();
    createScopeWithTenants(
      collectionId,
      "KEY_1",
      asList(unauthorizedTenant1, null, unauthorizedTenant2),
      definitionResourceType
    );
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScopeForKermit(collectionId);
    assertThat(scopeEntries).hasSize(1)
      .flatExtracting(CollectionScopeEntryResponseDto::getTenants)
      .contains(UNAUTHORIZED_TENANT_MASK);
    final CollectionScopeEntryResponseDto scopeEntry = scopeEntries.get(0);
    List<String> oneTenantAdded = scopeEntry
      .getTenants()
      .stream()
      .map(TenantDto::getId)
      .collect(toList());
    oneTenantAdded.add(authorizedTenant);

    // when update the result with masked tenants
    collectionClient.updateCollectionScopeAsKermit(collectionId, scopeEntry.getId(), oneTenantAdded);
    scopeEntries = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scopeEntries)
      .hasSize(1)
      .flatExtracting(CollectionScopeEntryResponseDto::getTenants)
      .extracting(TenantDto::getId)
      .containsExactlyInAnyOrder(authorizedTenant, null, unauthorizedTenant1, unauthorizedTenant2);
  }

  @ParameterizedTest(name = "add scope throws error on unauthorized key for definition resource type {0}")
  @MethodSource("definitionResourceTypes")
  public void addScope_unauthorizedKeyThrowsError(final int definitionResourceType) {
    // given
    deployAndImportDefinition(definitionResourceType, "KEY", DEFAULT_TENANT);
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    // when
    final DefinitionType definitionType = resourceTypeToDefinitionType.get(definitionResourceType);
    List<CollectionScopeEntryDto> unauthorizedScope =
      singletonList(new CollectionScopeEntryDto(definitionType, "KEY", DEFAULT_TENANTS));
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, unauthorizedScope)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response)
      .satisfies(r -> assertThat(r.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode()))
      .extracting(r -> r.readEntity(ErrorResponseDto.class))
      .extracting(ErrorResponseDto::getDetailedMessage)
      .isEqualTo(String.format(SCOPE_NOT_AUTHORIZED_MESSAGE, KERMIT_USER, unauthorizedScope.get(0).getId()));
  }

  @Test
  public void addScope_unauthorizedEventProcessKeyThrowsError() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("KEY", (IdentityDto) null);
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    // when
    List<CollectionScopeEntryDto> unauthorizedScope =
      singletonList(new CollectionScopeEntryDto(PROCESS, "KEY", DEFAULT_TENANTS));
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, unauthorizedScope)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response)
      .satisfies(r -> assertThat(r.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode()))
      .extracting(r -> r.readEntity(ErrorResponseDto.class))
      .extracting(ErrorResponseDto::getDetailedMessage)
      .isEqualTo(String.format(SCOPE_NOT_AUTHORIZED_MESSAGE, KERMIT_USER, unauthorizedScope.get(0).getId()));
  }

  @ParameterizedTest(name = "add scope throws error on unauthorized tenant for definition resource type {0}")
  @MethodSource("definitionResourceTypes")
  public void addScope_unauthorizedTenantThrowsError(final int definitionResourceType) {
    // given
    deployAndImportDefinition(definitionResourceType, "KEY", DEFAULT_TENANT);
    final String unauthorizedTenant = "unauthorizedTenant";
    engineIntegrationExtension.createTenant(unauthorizedTenant);
    // import tenant so he's available in the tenant cache
    importAllEngineEntitiesFromScratch();
    final String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationForKermit("KEY", definitionResourceType);
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    // when
    final DefinitionType definitionType = resourceTypeToDefinitionType.get(definitionResourceType);
    final CollectionScopeEntryDto scopeToAdd =
      new CollectionScopeEntryDto(definitionType, "KEY", newArrayList(DEFAULT_TENANT, unauthorizedTenant));
    List<CollectionScopeEntryDto> unauthorizedScope = singletonList(scopeToAdd);
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, unauthorizedScope)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response)
      .satisfies(r -> assertThat(r.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode()))
      .extracting(r -> r.readEntity(ErrorResponseDto.class))
      .extracting(ErrorResponseDto::getDetailedMessage)
      .isEqualTo(String.format(SCOPE_NOT_AUTHORIZED_MESSAGE, KERMIT_USER, scopeToAdd.getId()));
  }

  @Test
  public void addScope_existingEventBased() {
    // given
    final String key1 = "eventBasedKey1";
    final String key2 = "eventBasedKey2";

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key1, new UserDto(KERMIT_USER));
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(key2, new UserDto(KERMIT_USER));

    final String collectionId = collectionClient.createNewCollection();
    List<CollectionScopeEntryDto> entries = new ArrayList<>();
    entries.add(collectionClient.createSimpleScopeEntry(key1, PROCESS));
    entries.add(collectionClient.createSimpleScopeEntry(key2, PROCESS));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, entries)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void addScope_nonExistentEventBased() {
    // given
    final String key = "eventBasedKey";

    final String collectionId = collectionClient.createNewCollection();
    List<CollectionScopeEntryDto> entries = new ArrayList<>();
    entries.add(collectionClient.createSimpleScopeEntry(key, PROCESS));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    addRoleToCollectionAsDefaultUser(new CollectionRoleRequestDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildAddScopeEntriesToCollectionRequest(collectionId, entries)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private void deployAndImportDefinition(int definitionResourceType, final String definitionKey,
                                         final String tenantId) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(definitionKey), tenantId);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deploySimpleDecisionDefinition(definitionKey, tenantId);
        break;
      default:
        throw new IllegalStateException("Uncovered definitionResourceType: " + definitionResourceType);
    }

    importAllEngineEntitiesFromScratch();
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

  private void addRoleToCollectionAsDefaultUser(final CollectionRoleRequestDto roleDto,
                                                final String collectionId) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRolesToCollectionRequest(collectionId, roleDto)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
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
