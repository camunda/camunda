/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionWithTenantsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.service.TenantService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.lessThan;

public abstract class AbstractDefinitionRestServiceIT extends AbstractIT {

  protected static final String VERSION_TAG = "aVersionTag";
  private static final String TENANT_NONE_NAME = TenantService.TENANT_NOT_DEFINED.getName();
  private static final TenantRestDto TENANT_NONE_DTO = new TenantRestDto(null, TENANT_NONE_NAME);
  private static final TenantRestDto TENANT_1_DTO = new TenantRestDto("tenant1", "Tenant 1");
  private static final TenantRestDto TENANT_2_DTO = new TenantRestDto("tenant2", "Tenant 2");

  @Test
  public void testGetDefinitionVersionsWithTenants() {
    //given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey1 = "definitionKey1";
    final String definitionKey2 = "definitionKey2";
    createDefinitionsForKey(definitionKey1, 3);
    createDefinitionsForKey(definitionKey2, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey2, 3, TENANT_2_DTO.getId());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(2));
    // first definition
    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(definitionKey1));
    final List<TenantRestDto> expectedDefinition1AllTenantsOrdered = ImmutableList.of(
      TENANT_NONE_DTO, TENANT_1_DTO, TENANT_2_DTO
    );
    assertThat(firstDefinition.getAllTenants(), is(expectedDefinition1AllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, expectedDefinition1AllTenantsOrdered)
    );
    assertThat(firstDefinition.getVersions(), is(expectedVersionForDefinition1));
    // second definition
    final DefinitionVersionsWithTenantsRestDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey(), is(definitionKey2));
    final List<TenantRestDto> expectedDefinition2AllTenantsOrdered = ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO);
    assertThat(secondDefinition.getAllTenants(), is(expectedDefinition2AllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionForDefinition2 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, ImmutableList.of(TENANT_2_DTO)),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO)),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO))
    );
    assertThat(secondDefinition.getVersions(), is(expectedVersionForDefinition2));
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_onlyAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey = "definitionKey";

    createDefinitionsForKey(definitionKey, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, definitionKey);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsUser(tenant1UserId);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey));
    assertThat(availableDefinition.getAllTenants(), contains(TENANT_1_DTO));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), contains(TENANT_1_DTO))
    );
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants_sharedAndTenantDefinitionWithSameKeyAndVersion() {
    //given
    createTenant(TENANT_1_DTO);
    final String definitionKey1 = "definitionKey1";

    createDefinitionsForKey(definitionKey1, 2);
    createDefinitionsForKey(definitionKey1, 3, TENANT_1_DTO.getId());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey1));
    final List<TenantRestDto> expectedAllTenantsOrdered = ImmutableList.of(TENANT_NONE_DTO, TENANT_1_DTO);
    assertThat(availableDefinition.getAllTenants(), is(expectedAllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, ImmutableList.of(TENANT_1_DTO)),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, expectedAllTenantsOrdered),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, expectedAllTenantsOrdered)
    );
    assertThat(availableDefinition.getVersions(), is(expectedVersionForDefinition1));
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants_onlyAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey = "definitionKey";

    createDefinitionsForKey(definitionKey, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, definitionKey);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsUser(tenant1UserId);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey));
    assertThat(availableDefinition.getAllTenants(), contains(TENANT_1_DTO));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), contains(TENANT_1_DTO))
    );
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants_sharedDefinitionNoneTenantAndAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey = "definitionKey";
    createDefinitionsForKey(definitionKey, 4);
    createDefinitionsForKey(definitionKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, definitionKey);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions =
      getDefinitionVersionsWithTenantsAsUser(tenant1UserId);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey));
    final List<TenantRestDto> expectedAllTenantsOrdered = ImmutableList.of(TENANT_NONE_DTO, TENANT_1_DTO);
    assertThat(availableDefinition.getAllTenants(), is(expectedAllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), is(expectedAllTenantsOrdered))
    );
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants_sorting() {
    createDefinition("z", "1", null, "a");
    createDefinition("x", "1", null, "b");
    createDefinitionsForKey("c", 1);
    createDefinitionsForKey("D", 1);
    createDefinitionsForKey("e", 1);
    createDefinitionsForKey("F", 1);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();

    assertThat(definitions.get(0).getKey(), is("z"));
    assertThat(definitions.get(1).getKey(), is("x"));
    assertThat(definitions.get(2).getKey(), is("c"));
    assertThat(definitions.get(3).getKey(), is("D"));
    assertThat(definitions.get(4).getKey(), is("e"));
    assertThat(definitions.get(5).getKey(), is("F"));
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_performance() {
    // given
    final int definitionCount = 50;
    final int tenantCount = 10;
    final int versionCount = 5;

    IntStream.range(0, tenantCount)
      .mapToObj(String::valueOf)
      .parallel()
      .forEach(value -> createTenant(new TenantRestDto(value, value)));

    IntStream.range(0, definitionCount)
      .mapToObj(String::valueOf)
      .parallel()
      .forEach(definitionNumber -> {
        final String definitionKey = "defKey" + definitionNumber;
        IntStream.range(0, tenantCount)
          .mapToObj(String::valueOf)
          .parallel()
          .forEach(tenantNumber -> createDefinitionsForKey(definitionKey, versionCount, tenantNumber));
      });

    // when
    long startTimeMillis = System.currentTimeMillis();
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();
    long responseTimeMillis = System.currentTimeMillis() - startTimeMillis;

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(definitionCount));
    definitions.forEach(definitionVersionsWithTenantsRestDto -> {
      assertThat(definitionVersionsWithTenantsRestDto.getVersions().size(), is(versionCount));
      assertThat(definitionVersionsWithTenantsRestDto.getAllTenants().size(), is(tenantCount));
    });
    assertThat(responseTimeMillis, is(lessThan(2000L)));

    embeddedOptimizeExtension.getImportSchedulerFactory().shutdown();
  }

  private List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenants() {
    return getDefinitionVersionsWithTenantsAsUser(DEFAULT_USERNAME);
  }

  protected abstract List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenantsAsUser(String userId);

  private void createDefinitionsForKey(final String definitionKey, final int versionCount) {
    createDefinitionsForKey(definitionKey, versionCount, null);
  }

  protected abstract void createDefinitionsForKey(String definitionKey, int versionCount, String tenantId);

  protected abstract void createDefinition(String key, String version, String tenantId, String name);

  protected abstract int getDefinitionResourceType();

  protected void grantSingleDefinitionAuthorizationsForUser(final String userId, final String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(getDefinitionResourceType());
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void createUserWithTenantAuthorization(final String tenantUser,
                                                 final ImmutableList<String> permissions,
                                                 final String tenantId) {
    createOptimizeUser(tenantUser);
    createTenantAuthorization(tenantUser, permissions, tenantId, AUTHORIZATION_TYPE_GRANT);
  }

  private void createTenantAuthorization(final String tenantUser,
                                         final ImmutableList<String> permissions,
                                         final String resourceId,
                                         int type) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(type);
    authorizationDto.setUserId(tenantUser);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void createOptimizeUser(final String tenantUser) {
    engineIntegrationExtension.addUser(tenantUser, tenantUser);
    engineIntegrationExtension.grantUserOptimizeAccess(tenantUser);
  }

  protected void createTenant(final TenantRestDto tenantRestDto) {
    createTenant(tenantRestDto.getId(), tenantRestDto.getName());
  }

  protected void createTenant(final String id, final String name) {
    final TenantDto tenantDto = new TenantDto(id, name, DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, id, tenantDto);
  }

}
