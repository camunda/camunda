/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.TenantService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class DefinitionRestServiceWithCollectionScopeIT extends AbstractIT {

  private static final String TENANT_NOT_DEFINED_ID = TenantService.TENANT_NOT_DEFINED.getId();

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_invalidCollectionId(final DefinitionType type) {
    //given

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
  public void testGetDefinitionVersionsWithTenants_inScope(final DefinitionType type) {
    //given
    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");
    final String collectionId = addEmptyCollectionToOptimize();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_multipleInScope(final DefinitionType type) {
    //given
    final String definitionKey1 = "definitionKey1";
    createDefinition(type, definitionKey1, "1", null, "the name");
    final String definitionKey2 = "definitionKey2";
    createDefinition(type, definitionKey2, "1", null, "the name");

    final String collectionId = addEmptyCollectionToOptimize();
    final List<String> scopeTenantIds = Collections.singletonList(TENANT_NOT_DEFINED_ID);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey1, scopeTenantIds)
    );
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(type, definitionKey2, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions.stream().map(DefinitionVersionsWithTenantsRestDto::getKey))
      .containsExactlyInAnyOrder(definitionKey1, definitionKey2);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_emptyScope(final DefinitionType type) {
    //given
    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");
    final String collectionId = addEmptyCollectionToOptimize();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

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
    final String collectionId = addEmptyCollectionToOptimize();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions)
      .hasOnlyOneElementSatisfying(definitionEntry -> {
        assertThat(definitionEntry.getAllTenants().stream().map(TenantRestDto::getId))
          .containsExactlyElementsOf(scopeTenantIds);

        assertThat(definitionEntry.getVersions())
          .hasOnlyOneElementSatisfying(
            versionEntry -> assertThat(versionEntry.getTenants().stream().map(TenantRestDto::getId))
              .containsExactlyElementsOf(scopeTenantIds)
          );
      });
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void testGetDefinitionVersionsWithTenants_sharedDefinitionOnlySpecificTenantInScope(final DefinitionType type) {
    //given
    final String tenant1 = "tenant1";
    createTenant(tenant1);
    final String tenant2 = "tenant2";
    createTenant(tenant2);

    final String definitionKey = "definitionKey1";
    createDefinition(type, definitionKey, "1", null, "the name");

    final List<String> scopeTenantIds = Lists.newArrayList(tenant2);
    final String collectionId = addEmptyCollectionToOptimize();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions)
      .hasOnlyOneElementSatisfying(definitionEntry -> {
        assertThat(definitionEntry.getAllTenants().stream().map(TenantRestDto::getId))
          .containsExactlyElementsOf(scopeTenantIds);
        assertThat(definitionEntry.getVersions())
          .hasOnlyOneElementSatisfying(
            versionEntry -> assertThat(versionEntry.getTenants().stream().map(TenantRestDto::getId))
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
    final String collectionId = addEmptyCollectionToOptimize();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(type, definitionKey, scopeTenantIds)
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants(type, collectionId);

    // then
    assertThat(definitions)
      .hasOnlyOneElementSatisfying(definitionEntry -> {
        assertThat(definitionEntry.getAllTenants().stream().map(TenantRestDto::getId))
          .containsExactlyElementsOf(scopeTenantIds);
        assertThat(definitionEntry.getVersions())
          .hasOnlyOneElementSatisfying(
            versionEntry -> assertThat(versionEntry.getTenants().stream().map(TenantRestDto::getId))
              .containsExactlyElementsOf(scopeTenantIds)
          );
      });
  }

  private List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenants(final DefinitionType type,
                                                                                      final String collectionId) {
    switch (type) {
      case PROCESS:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetProcessDefinitionVersionsWithTenants(collectionId)
          .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, Response.Status.OK.getStatusCode());
      case DECISION:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildGetDecisionDefinitionVersionsWithTenants(collectionId)
          .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, Response.Status.OK.getStatusCode());
      default:
        throw new OptimizeIntegrationTestException("Unsupported definition type: " + type);
    }
  }

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
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
