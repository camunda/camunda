/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.TenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class DefinitionRestServiceWithCollectionScopeIT extends AbstractIT {

  private static final String TENANT_NOT_DEFINED_ID = TenantService.TENANT_NOT_DEFINED.getId();

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionKeysByType_invalidCollectionId(final DefinitionType type) {
    // given

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionKeysByType(type.getId(), "invalid")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionKeysByType_multipleInScope(final DefinitionType type) {
    // given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    final String definitionKey2 = "definitionKey2";
    createDefinition(type, definitionKey2, "1", null, "the name");
    // one definition that will not be in the scope
    createDefinition(type, "otherKey", "1", null, "the name");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey2, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionKeyDto> definitionKeys = definitionClient.getDefinitionKeysByType(type, collectionId);

    // then
    assertThat(definitionKeys)
      .extracting(DefinitionKeyDto::getKey)
      .containsExactlyInAnyOrder(definitionKey1, definitionKey2);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionKeysByType_sharedDefinitionOnlySpecificTenantInScope(final DefinitionType type) {
    // given
    final String tenant1 = "tenant1";
    createTenant(tenant1);

    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");

    // we have a collection for which only a specific tenant is in the scope
    final List<String> scopeTenantIds = Lists.newArrayList(tenant1);
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when we get definition keys for the scope of the collection
    final List<DefinitionKeyDto> definitionKeys = definitionClient.getDefinitionKeysByType(type, collectionId);

    // then the definition key is still returned although the definition only exists for the not defined tenant
    assertThat(definitionKeys)
      .extracting(DefinitionKeyDto::getKey)
      .containsExactlyInAnyOrder(definitionKey);
  }

  @Test
  public void getDefinitionKeysByType_eventBasedProcesses() {
    // given
    final String definitionKey1 = "eventProcess1";
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(definitionKey1);
    final String definitionKey2 = "eventProcess2";
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(definitionKey2);
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(PROCESS, definitionKey1, scopeTenantIds)
    );

    // when I get process definition keys with the collection scope
    final List<DefinitionKeyDto> definitionKeys = definitionClient.getDefinitionKeysByType(PROCESS, collectionId);

    // then event processes that is in the scope is there
    assertThat(definitionKeys).extracting(DefinitionKeyDto::getKey).containsExactly(definitionKey1);

    // when I get process definitions but exclude event processes
    final List<DefinitionKeyDto> definitionKeysWithoutEventProcesses = definitionClient
      .getDefinitionKeysByType(PROCESS, collectionId, true);

    // then
    assertThat(definitionKeysWithoutEventProcesses).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionKeysByType_emptyScope(final DefinitionType type) {
    // given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    final String collectionId = collectionClient.createNewCollection();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionKeyDto> definitionKeys = definitionClient.getDefinitionKeysByType(type, collectionId);

    // then
    assertThat(definitionKeys).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionKeysByType_typesAreIsolated(final DefinitionType type) {
    // given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "process");
    // also create a definition of another type
    final DefinitionType otherDefinitionType = Arrays.stream(DefinitionType.values())
      .filter(value -> !type.equals(value))
      .findFirst()
      .orElseThrow(OptimizeIntegrationTestException::new);
    createDefinition(otherDefinitionType, definitionKey1, "1", null, "other");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when definition keys of the other type are requested
    final List<DefinitionKeyDto> definitionKeys = definitionClient
      .getDefinitionKeysByType(otherDefinitionType, collectionId);

    // then none are returned
    assertThat(definitionKeys).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByKeyAndType_invalidCollectionId(final DefinitionType type) {
    // given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(type.getId(), definitionKey1, "invalid")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByKeyAndType_multipleInScope(final DefinitionType type) {
    // given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    createDefinition(type, definitionKey1, "2", null, "the name");
    createDefinition(type, "otherKey", "1", null, "the name");
    // create more versions of other type, should not affect result
    final DefinitionType otherDefinitionType = Arrays.stream(DefinitionType.values())
      .filter(value -> !type.equals(value))
      .findFirst()
      .orElseThrow(OptimizeIntegrationTestException::new);
    createDefinition(otherDefinitionType, definitionKey1, "1", null, "other");
    createDefinition(otherDefinitionType, definitionKey1, "2", null, "other");
    createDefinition(otherDefinitionType, definitionKey1, "3", null, "other");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionDto> versions = definitionClient
      .getDefinitionVersionsByTypeAndKey(type, definitionKey1, collectionId);

    // then
    assertThat(versions).extracting(DefinitionVersionDto::getVersion).containsExactly("2", "1");
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByKeyAndType_multiTenant_specificDefinitions(final DefinitionType type) {
    // given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);
    final String tenant3 = "tenant3";
    createTenant(tenant3);
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", tenant1, "the name");
    createDefinition(type, definitionKey1, "1", tenant2, "the name");
    createDefinition(type, definitionKey1, "2", tenant2, "the name");
    createDefinition(type, definitionKey1, "3", tenant3, "the name");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Lists.newArrayList(tenant1, tenant2);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionDto> versions = definitionClient
      .getDefinitionVersionsByTypeAndKey(type, definitionKey1, collectionId);

    // then
    assertThat(versions).extracting(DefinitionVersionDto::getVersion).containsExactly("2", "1");
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByKeyAndType_multiTenant_sharedDefinition(final DefinitionType type) {
    // given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);
    final String tenant3 = "tenant3";
    createTenant(tenant3);
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    createDefinition(type, definitionKey1, "2", null, "the name");
    createDefinition(type, definitionKey1, "3", null, "the name");
    createDefinition(type, definitionKey1, "4", null, "the name");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Lists.newArrayList(tenant1, tenant2);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionDto> versions = definitionClient
      .getDefinitionVersionsByTypeAndKey(type, definitionKey1, collectionId);

    // then
    assertThat(versions).extracting(DefinitionVersionDto::getVersion).containsExactly("4", "3", "2", "1");
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByKeyAndType_multiTenant_sharedAndSpecificDefinitions(final DefinitionType type) {
    // given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);
    final String tenant3 = "tenant3";
    createTenant(tenant3);
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    createDefinition(type, definitionKey1, "1", tenant1, "the name");
    createDefinition(type, definitionKey1, "1", tenant2, "the name");
    createDefinition(type, definitionKey1, "2", null, "the name");
    createDefinition(type, definitionKey1, "2", tenant1, "the name");
    createDefinition(type, definitionKey1, "2", tenant2, "the name");
    createDefinition(type, definitionKey1, "3", tenant1, "the name");
    createDefinition(type, definitionKey1, "4", tenant3, "the name");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Lists.newArrayList(tenant1, tenant2);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionDto> versions = definitionClient
      .getDefinitionVersionsByTypeAndKey(type, definitionKey1, collectionId);

    // then
    assertThat(versions).extracting(DefinitionVersionDto::getVersion).containsExactly("3", "2", "1");
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void getDefinitionVersionsByKeyAndType_keyNotInScope(final DefinitionType type) {
    // given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    final String definitionKey2 = "definitionKey2";
    createDefinition(type, definitionKey2, "1", null, "the name");
    // create definitionKey2 of other type, should not affect result
    final DefinitionType otherDefinitionType = Arrays.stream(DefinitionType.values())
      .filter(value -> !type.equals(value))
      .findFirst()
      .orElseThrow(OptimizeIntegrationTestException::new);
    createDefinition(otherDefinitionType, definitionKey2, "1", null, "other");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Lists.newArrayList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey2, scopeTenantIds)
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when trying to get versions for a definition key that is not in the scope
    final Response responseForWrongKey = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(type.getId(), definitionKey1, collectionId)
      .execute();

    // then a 404 is returned
    assertThat(responseForWrongKey.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

    // when trying to get versions for a definition type that is not in the scope but key that is in the scope
    final Response responseForWrongType = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(otherDefinitionType.getId(), definitionKey2, collectionId)
      .execute();

    // then a 404 is returned
    assertThat(responseForWrongType.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_invalidCollectionId(final DefinitionType type) {
    // given

    // when
    final Response response;
    switch (type) {
      case PROCESS:
        response = embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetProcessDefinitionVersionsWithTenants("invalid")
          .execute();
        break;
      case DECISION:
        response = embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetDecisionDefinitionVersionsWithTenants("invalid")
          .execute();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + type);
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_multipleInScope(final DefinitionType type) {
    //given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    final String definitionKey2 = "definitionKey2";
    createDefinition(type, definitionKey2, "1", null, "the name");
    createDefinition(type, "otherKey", "1", null, "the name");
    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey2, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions.stream().map(DefinitionVersionsWithTenantsDto::getKey))
      .containsExactlyInAnyOrder(definitionKey1, definitionKey2);
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_multipleInScope_excludeEventProcesses() {
    //given
    final String definitionKey1 = "definitionKey1";
    createDefinition(DefinitionType.PROCESS, definitionKey1, "1", null, "the name");
    final String definitionKey2 = "definitionKey2";
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(definitionKey2);

    final String collectionId = collectionClient.createNewCollection();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(DefinitionType.PROCESS, definitionKey1, scopeTenantIds)
    );
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(DefinitionType.PROCESS, definitionKey2, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants(collectionId, true)
      .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.stream().map(DefinitionVersionsWithTenantsDto::getKey))
      .containsExactlyInAnyOrder(definitionKey1);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_emptyScope(final DefinitionType type) {
    //given
    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");
    final String collectionId = collectionClient.createNewCollection();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_twoTenantsOneInScope(final DefinitionType type) {
    //given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);

    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", tenant1, "the name");
    createDefinition(type, definitionKey, "1", tenant2, "the name");

    final List<String> scopeTenantIds = Collections.singletonList(tenant2);
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions)
      .hasOnlyOneElementSatisfying(definitionEntry -> {
        assertThat(definitionEntry.getAllTenants().stream().map(TenantDto::getId))
          .containsExactlyElementsOf(scopeTenantIds);

        assertThat(definitionEntry.getVersions())
          .hasOnlyOneElementSatisfying(
            versionEntry -> assertThat(versionEntry.getTenants().stream().map(TenantDto::getId))
              .containsExactlyElementsOf(scopeTenantIds)
          );
      });
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_sharedDefinitionOnlySpecificTenantInScope(final DefinitionType type) {
    // given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);

    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");

    final List<String> scopeTenantIds = Lists.newArrayList(tenant2);
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions)
      .hasOnlyOneElementSatisfying(definitionEntry -> {
        assertThat(definitionEntry.getAllTenants().stream().map(TenantDto::getId))
          .containsExactlyElementsOf(scopeTenantIds);
        assertThat(definitionEntry.getVersions())
          .hasOnlyOneElementSatisfying(
            versionEntry -> assertThat(versionEntry.getTenants().stream().map(TenantDto::getId))
              .containsExactlyElementsOf(scopeTenantIds)
          );
      });
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_sharedDefinitionNoneAndTenantInScope(final DefinitionType type) {
    //given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);

    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");

    final List<String> scopeTenantIds = Lists.newArrayList(TENANT_NOT_DEFINED_ID, tenant2);
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions)
      .hasOnlyOneElementSatisfying(definitionEntry -> {
        assertThat(definitionEntry.getAllTenants().stream().map(TenantDto::getId))
          .containsExactlyElementsOf(scopeTenantIds);
        assertThat(definitionEntry.getVersions())
          .hasOnlyOneElementSatisfying(
            versionEntry -> assertThat(versionEntry.getTenants().stream().map(TenantDto::getId))
              .containsExactlyElementsOf(scopeTenantIds)
          );
      });
  }

  private List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenants(final DefinitionType type,
                                                                                  final String collectionId) {
    switch (type) {
      case PROCESS:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetProcessDefinitionVersionsWithTenants(collectionId)
          .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());
      case DECISION:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetDecisionDefinitionVersionsWithTenants(collectionId)
          .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + type);
    }
  }

  private void createDefinition(final DefinitionType definitionType,
                                final String key,
                                final String version,
                                final String tenantId,
                                final String name) {
    switch (definitionType) {
      case PROCESS:
        addProcessDefinitionToElasticsearch(key, version, tenantId, name);
        return;
      case DECISION:
        addDecisionDefinitionToElasticsearch(key, version, tenantId, name);
        return;
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + definitionType);
    }
  }

  private void addDecisionDefinitionToElasticsearch(final String key,
                                                    final String version,
                                                    final String tenantId,
                                                    final String name) {
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = DecisionDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .version(version)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .name(name)
      .dmn10Xml("id-" + key + "-version-" + version + "-" + tenantId)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME, decisionDefinitionDto.getId(), decisionDefinitionDto
    );
  }

  private void addProcessDefinitionToElasticsearch(final String key,
                                                   final String version,
                                                   final String tenantId,
                                                   final String name) {
    final ProcessDefinitionOptimizeDto expectedDto = ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .name(name)
      .version(version)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(key + version + tenantId)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedDto.getId(),
      expectedDto
    );
  }

  protected void createTenant(final String tenantId) {
    final TenantDto tenantDto = new TenantDto(tenantId, tenantId, DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantId, tenantDto);
  }

}
