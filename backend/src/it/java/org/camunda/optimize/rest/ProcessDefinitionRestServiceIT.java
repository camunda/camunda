/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionWithTenantsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.service.TenantService;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class ProcessDefinitionRestServiceIT extends AbstractIT {

  private static final String VERSION_TAG = "aVersionTag";
  private static final String KEY = "testKey";

  private static final String TENANT_NONE_NAME = TenantService.TENANT_NOT_DEFINED.getName();
  private static final TenantRestDto TENANT_NONE_DTO = new TenantRestDto(null, TENANT_NONE_NAME);
  private static final TenantRestDto TENANT_1_DTO = new TenantRestDto("tenant1", "Tenant 1");
  private static final TenantRestDto TENANT_2_DTO = new TenantRestDto("tenant2", "Tenant 2");

  @Test
  public void getProcessDefinitions() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then the status code is okay
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(processDefinitionOptimizeDto.getId()));
  }

  @Test
  public void getProcessDefinitionsReturnOnlyThoseAuthorizedToSee() {
    //given
    final String kermitUser = "kermit";
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey = "access";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto notAuthorizedToSee = addProcessDefinitionToElasticsearch(
      notAuthorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto authorizedToSee = addProcessDefinitionToElasticsearch(authorizedDefinitionKey);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then we only get 1 definition, the one kermit is authorized to see
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(authorizedToSee.getId()));
  }

  @Test
  public void getProcessDefinitionsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetProcessDefinitionsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionsWithXml() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(processDefinitionOptimizeDto.getId()));
    assertThat(definitions.get(0).getBpmn20Xml(), is(processDefinitionOptimizeDto.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXml() {
    //given
    ProcessDefinitionOptimizeDto expectedDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(expectedDto.getKey(), expectedDto.getVersion())
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(expectedDto.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXmlByTenant() {
    //given
    final String firstTenantId = "tenant1";
    final String secondTenantId = "tenant2";
    ProcessDefinitionOptimizeDto firstTenantDefinition = addProcessDefinitionToElasticsearch(KEY, firstTenantId);
    ProcessDefinitionOptimizeDto secondTenantDefinition = addProcessDefinitionToElasticsearch(KEY, secondTenantId);

    // when
    final String actualXmlFirstTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(KEY, "1", firstTenantId)
      .execute(String.class, 200);
    final String actualXmlSecondTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(KEY, "1", secondTenantId)
      .execute(String.class, 200);

    // then
    assertThat(actualXmlFirstTenant, is(firstTenantDefinition.getBpmn20Xml()));
    assertThat(actualXmlSecondTenant, is(secondTenantDefinition.getBpmn20Xml()));
  }

  @Test
  public void getSharedProcessDefinitionXmlByNullTenant() {
    //given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto firstTenantDefinition = addProcessDefinitionToElasticsearch(KEY, firstTenantId);
    ProcessDefinitionOptimizeDto secondTenantDefinition = addProcessDefinitionToElasticsearch(KEY, null);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(
          secondTenantDefinition.getKey(), secondTenantDefinition.getVersion(), null
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(secondTenantDefinition.getBpmn20Xml()));
  }

  @Test
  public void getSharedProcessDefinitionXmlByTenantWithNoSpecificDefinition() {
    //given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto sharedTenantDefinition = addProcessDefinitionToElasticsearch(KEY, null);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(
          sharedTenantDefinition.getKey(), sharedTenantDefinition.getVersion(), firstTenantId
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(sharedTenantDefinition.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXmlWithNullParameter() {
    //given
    ProcessDefinitionOptimizeDto expectedDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(null, expectedDto.getVersion())
        .execute(String.class, 404);
  }

  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .withoutAuthentication()
        .buildGetProcessDefinitionXmlRequest("foo", "bar")
        .execute();


    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionXmlWithoutAuthorization() {
    // given
    final String kermitUser = "kermit";
    final String definitionKey = "aProcDefKey";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(
      definitionKey
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionXmlRequest(
        processDefinitionOptimizeDto.getKey(), processDefinitionOptimizeDto.getVersion()
      ).execute();

    // then the status code is forbidden
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseVersionReturns404Code() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(processDefinitionOptimizeDto.getKey(), "nonsenseVersion")
        .execute(String.class, 404);

    // then
    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseKeyReturns404Code() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest("nonesense", processDefinitionOptimizeDto.getVersion())
        .execute(String.class, 404);

    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants() {
    //given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String procDefKey1 = "procDefKey1";
    final String procDefKey2 = "procDefKey2";
    createProcessDefinitionsForKey(procDefKey1, 3);
    createProcessDefinitionsForKey(procDefKey2, 2, TENANT_1_DTO.getId());
    createProcessDefinitionsForKey(procDefKey2, 3, TENANT_2_DTO.getId());

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(2));
    // first definition
    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(procDefKey1));
    final List<TenantRestDto> expectedDefinition1AllTenants = ImmutableList.of(
      TENANT_NONE_DTO, TENANT_1_DTO, TENANT_2_DTO
    );
    assertThat(firstDefinition.getAllTenants(), is(expectedDefinition1AllTenants));
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, expectedDefinition1AllTenants),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, expectedDefinition1AllTenants),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, expectedDefinition1AllTenants)
    );
    assertThat(firstDefinition.getVersions(), is(expectedVersionForDefinition1));
    // second definition
    final DefinitionVersionsWithTenantsRestDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey(), is(procDefKey2));
    assertThat(firstDefinition.getAllTenants(), is(expectedDefinition1AllTenants));
    final List<TenantRestDto> expectedDefinition2AllTenants = ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO);
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionForDefinition2 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, ImmutableList.of(TENANT_2_DTO)),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, expectedDefinition2AllTenants),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, expectedDefinition2AllTenants)
    );
    assertThat(secondDefinition.getVersions(), is(expectedVersionForDefinition2));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_sharedAndTenantDefinitionWithSameKeyAndVersion() {
    //given
    createTenant(TENANT_1_DTO);
    final String procDefKey1 = "procDefKey1";

    createProcessDefinitionsForKey(procDefKey1, 2);
    createProcessDefinitionsForKey(procDefKey1, 3, TENANT_1_DTO.getId());

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(procDefKey1));
    final List<TenantRestDto> expectedAllTenantsOrdered = ImmutableList.of(TENANT_NONE_DTO, TENANT_1_DTO);
    assertThat(firstDefinition.getAllTenants(), is(expectedAllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionsForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, ImmutableList.of(TENANT_1_DTO)),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, expectedAllTenantsOrdered),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, expectedAllTenantsOrdered)
    );
    assertThat(firstDefinition.getVersions(), is(expectedVersionsForDefinition1));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_onlyAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String procDefKey = "procDefKey";
    createProcessDefinitionsForKey(procDefKey, 2, TENANT_1_DTO.getId());
    createProcessDefinitionsForKey(procDefKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, procDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(procDefKey));
    assertThat(availableDefinition.getAllTenants(), contains(TENANT_1_DTO));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), contains(TENANT_1_DTO))
    );
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_sharedDefinitionNoneTenantAndAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String procDefKey = "procDefKey";
    createProcessDefinitionsForKey(procDefKey, 4);
    createProcessDefinitionsForKey(procDefKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, procDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(procDefKey));
    final List<TenantRestDto> expectedAllTenantsOrdered = ImmutableList.of(TENANT_NONE_DTO, TENANT_1_DTO);
    assertThat(availableDefinition.getAllTenants(), is(expectedAllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), is(expectedAllTenantsOrdered))
    );
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

  private void createTenant(final TenantRestDto tenantRestDto) {
    createTenant(tenantRestDto.getId(), tenantRestDto.getName());
  }

  private void createTenant(final String id, final String name) {
    final TenantDto tenantDto = new TenantDto(id, name, DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, id, tenantDto);
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key) {
    return addProcessDefinitionToElasticsearch(key, null);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key, final String tenantId) {
    return addProcessDefinitionToElasticsearch(key, "1", tenantId);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId) {
    final ProcessDefinitionOptimizeDto expectedDto = new ProcessDefinitionOptimizeDto()
      .setId(key + version + tenantId)
      .setKey(key)
      .setVersion(version)
      .setVersionTag(VERSION_TAG)
      .setTenantId(tenantId)
      .setEngine(DEFAULT_ENGINE_ALIAS)
      .setBpmn20Xml(key + version + tenantId);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, expectedDto.getId(), expectedDto);
    return expectedDto;
  }

  private void createProcessDefinitionsForKey(String key, int count) {
    createProcessDefinitionsForKey(key, count, null);
  }

  private void createProcessDefinitionsForKey(String key, int count, String tenantId) {
    IntStream.range(0, count).forEach(
      i -> addProcessDefinitionToElasticsearch(key, String.valueOf(i), tenantId)
    );
  }
}
