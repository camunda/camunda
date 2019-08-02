/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessDefinitionRestServiceIT {

  private static final String VERSION_TAG = "aVersionTag";
  private static final String KEY = "testKey";
  private static final String TENANT_NONE_NAME = TenantService.TENANT_NOT_DEFINED.getName();

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitions() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
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
    engineRule.addUser(kermitUser, kermitUser);
    engineRule.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto notAuthorizedToSee = addProcessDefinitionToElasticsearch(
      notAuthorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto authorizedToSee = addProcessDefinitionToElasticsearch(authorizedDefinitionKey);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
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
    Response response = embeddedOptimizeRule
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
      embeddedOptimizeRule
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
      embeddedOptimizeRule
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
    final String actualXmlFirstTenant = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(KEY, "1", firstTenantId)
      .execute(String.class, 200);
    final String actualXmlSecondTenant = embeddedOptimizeRule
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
      embeddedOptimizeRule
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
      embeddedOptimizeRule
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
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(null, expectedDto.getVersion())
        .execute(String.class, 404);
  }

  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule
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
    engineRule.addUser(kermitUser, kermitUser);
    engineRule.grantUserOptimizeAccess(kermitUser);
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(
      definitionKey
    );

    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
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
      embeddedOptimizeRule
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
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest("nonesense", processDefinitionOptimizeDto.getVersion())
        .execute(String.class, 404);

    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants() {
    //given
    final String tenantId1 = "tenant1";
    final String tenantName1 = "Tenant 1";
    final String tenantId2 = "tenant2";
    final String tenantName2 = "Tenant 2";
    createTenant(tenantId1, tenantName1);
    createTenant(tenantId2, tenantName2);
    final String procDefKey1 = "procDefKey1";
    final String procDefKey2 = "procDefKey2";
    createProcessDefinitionsForKey(procDefKey1, 3);
    createProcessDefinitionsForKey(procDefKey2, 2, tenantId1);
    createProcessDefinitionsForKey(procDefKey2, 3, tenantId2);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(2));

    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(procDefKey1));
    final List<DefinitionVersionsRestDto> firstDefinitionVersions = firstDefinition.getVersions();
    assertThat(firstDefinitionVersions.size(), is(3));
    final List<TenantRestDto> tenantsAvailableOnFirstDefinition =
      ImmutableList.of(new TenantRestDto(null, TENANT_NONE_NAME),
                       new TenantRestDto(tenantId1, tenantName1),
                       new TenantRestDto(tenantId2, tenantName2));
    assertThat(firstDefinition.getTenants().size(), is(3));
    assertThat(firstDefinition.getTenants(), is(tenantsAvailableOnFirstDefinition));
    assertThat(firstDefinitionVersions.get(0).getVersion(), is("2"));
    assertThat(firstDefinitionVersions.get(0).getVersionTag(), is(VERSION_TAG));
    assertThat(firstDefinitionVersions.get(1).getVersion(), is("1"));
    assertThat(firstDefinitionVersions.get(1).getVersionTag(), is(VERSION_TAG));
    assertThat(firstDefinitionVersions.get(2).getVersion(), is("0"));
    assertThat(firstDefinitionVersions.get(2).getVersionTag(), is(VERSION_TAG));

    final DefinitionVersionsWithTenantsRestDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey(), is(procDefKey2));
    final List<DefinitionVersionsRestDto> secondDefinitionVersions = secondDefinition.getVersions();
    assertThat(secondDefinitionVersions.size(), is(3));
    final List<TenantRestDto> tenantsAvailableOnSecondDefinitionVersionAll =
      ImmutableList.of(new TenantRestDto(tenantId1, tenantName1),
                       new TenantRestDto(tenantId2, tenantName2));
    assertThat(secondDefinition.getTenants().size(), is(2));
    assertThat(secondDefinition.getTenants(), is(tenantsAvailableOnSecondDefinitionVersionAll));
    assertThat(secondDefinitionVersions.get(0).getVersion(), is("2"));
    assertThat(secondDefinitionVersions.get(0).getVersionTag(), is(VERSION_TAG));
    assertThat(secondDefinitionVersions.get(1).getVersion(), is("1"));
    assertThat(secondDefinitionVersions.get(1).getVersionTag(), is(VERSION_TAG));
    assertThat(secondDefinitionVersions.get(2).getVersion(), is("0"));
    assertThat(secondDefinitionVersions.get(2).getVersionTag(), is(VERSION_TAG));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_sharedAndTenantDefinitionWithSameKeyAndVersion() {
    //given
    final String tenantId1 = "tenant1";
    final String tenantName1 = "Tenant 1";
    createTenant(tenantId1, tenantName1);
    final String procDefKey1 = "procDefKey1";

    createProcessDefinitionsForKey(procDefKey1, 2);
    createProcessDefinitionsForKey(procDefKey1, 3, tenantId1);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));

    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(procDefKey1));
    final List<DefinitionVersionsRestDto> firstDefinitionVersions = firstDefinition.getVersions();
    assertThat(firstDefinitionVersions.size(), is(3));
    final List<TenantRestDto> tenantsAvailableOnFirstDefinition =
      ImmutableList.of(new TenantRestDto(null, TENANT_NONE_NAME),
                       new TenantRestDto(tenantId1, tenantName1));
    assertThat(firstDefinition.getTenants().size(), is(2));
    assertThat(firstDefinition.getTenants(), is(tenantsAvailableOnFirstDefinition));
    assertThat(firstDefinitionVersions.get(0).getVersion(), is("2"));
    assertThat(firstDefinitionVersions.get(0).getVersionTag(), is(VERSION_TAG));
    assertThat(firstDefinitionVersions.get(1).getVersion(), is("1"));
    assertThat(firstDefinitionVersions.get(1).getVersionTag(), is(VERSION_TAG));
    assertThat(firstDefinitionVersions.get(2).getVersion(), is("0"));
    assertThat(firstDefinitionVersions.get(2).getVersionTag(), is(VERSION_TAG));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_onlyAuthorizedTenantsAvailable() {
    // given
    final String tenantId1 = "1";
    final String tenantName1 = "My Tenant 1";
    final String tenantId2 = "2";
    final String tenantName2 = "My Tenant 2";
    createTenant(tenantId1, tenantName1);
    createTenant(tenantId2, tenantName2);
    final String procDefKey = "procDefKey";
    createProcessDefinitionsForKey(procDefKey, 2, tenantId1);
    createProcessDefinitionsForKey(procDefKey, 3, tenantId2);

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), tenantId1);
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, procDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(procDefKey));
    final List<DefinitionVersionsRestDto> definitionVersions = availableDefinition.getVersions();
    assertThat(definitionVersions.size(), is(2));
    final List<TenantRestDto> tenantsAvailableOnFirstDefinition =
      ImmutableList.of(new TenantRestDto(tenantId1, tenantName1));
    assertThat(availableDefinition.getTenants().size(), is(1));
    assertThat(availableDefinition.getTenants(), is(tenantsAvailableOnFirstDefinition));
  }

  @Test
  public void testGetProcessDefinitionVersionsWithTenants_sharedDefinitionNoneTenantAndAuthorizedTenantsAvailable() {
    // given
    final String tenantId1 = "1";
    final String tenantName1 = "My Tenant 1";
    final String tenantId2 = "2";
    final String tenantName2 = "My Tenant 2";

    createTenant(tenantId1, tenantName1);
    createTenant(tenantId2, tenantName2);
    final String procDefKey = "procDefKey";
    createProcessDefinitionsForKey(procDefKey, 4);
    createProcessDefinitionsForKey(procDefKey, 3, tenantId2);

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), tenantId1);
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, procDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));

    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(procDefKey));
    final List<DefinitionVersionsRestDto> definitionVersions = availableDefinition.getVersions();
    assertThat(definitionVersions.size(), is(4));
    final List<TenantRestDto> tenantsAvailableOnFirstDefinition =
      ImmutableList.of(new TenantRestDto(null, TENANT_NONE_NAME),
                       new TenantRestDto(tenantId1, tenantName1));
    assertThat(availableDefinition.getTenants().size(), is(2));
    assertThat(availableDefinition.getTenants(), is(tenantsAvailableOnFirstDefinition));
  }

  private void createUserWithTenantAuthorization(final String tenantUser,
                                                 final ImmutableList<String> permissions,
                                                 final String tenantId) {
    createOptimizeUser(tenantUser);
    createTenantAuthorization(tenantUser, permissions, tenantId, AUTHORIZATION_TYPE_GRANT);
  }

  private void createTenantAuthorization(final String tenantUser,
                                         final ImmutableList<String> permissions,
                                         final String resourceIdId,
                                         int type) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceIdId);
    authorizationDto.setType(type);
    authorizationDto.setUserId(tenantUser);
    engineRule.createAuthorization(authorizationDto);
  }

  private void createOptimizeUser(final String tenantUser) {
    engineRule.addUser(tenantUser, tenantUser);
    engineRule.grantUserOptimizeAccess(tenantUser);
  }

  private void createTenant(final String id, final String name) {
    final TenantDto tenantDto = new TenantDto(id, name, DEFAULT_ENGINE_ALIAS);
    elasticSearchRule.addEntryToElasticsearch(TENANT_TYPE, id, tenantDto);
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
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
    elasticSearchRule.addEntryToElasticsearch(PROCESS_DEFINITION_TYPE, expectedDto.getId(), expectedDto);
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
