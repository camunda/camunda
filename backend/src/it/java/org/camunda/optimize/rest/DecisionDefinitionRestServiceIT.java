/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
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
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_TYPE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class DecisionDefinitionRestServiceIT {
  private static final String TENANT_NONE_NAME = TenantService.TENANT_NOT_DEFINED.getName();
  private static final String VERSION_TAG = "aVersionTag";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void getDecisionDefinitions() {
    //given
    final DecisionDefinitionOptimizeDto expectedDecisionDefinition = createDecisionDefinitionDto();

    // when
    List<DecisionDefinitionOptimizeDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    // then the status code is okay
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedDecisionDefinition.getId()));
  }

  @Test
  public void getDecisionDefinitionsReturnOnlyThoseAuthorizedToSee() {
    //given
    final String kermitUser = "kermit";
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey = "access";
    engineRule.addUser(kermitUser, kermitUser);
    engineRule.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);

    final DecisionDefinitionOptimizeDto authorizedToSee = createDecisionDefinitionDto(authorizedDefinitionKey);
    final DecisionDefinitionOptimizeDto notAuthorizedToSee = createDecisionDefinitionDto(notAuthorizedDefinitionKey);

    // when
    List<DecisionDefinitionOptimizeDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetDecisionDefinitionsRequest()
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    // then we only get 1 definition, the one kermit is authorized to see
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(authorizedToSee.getId()));
  }

  @Test
  public void getDecisionDefinitionsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDecisionDefinitionsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDecisionDefinitionsWithXml() {
    //given
    final DecisionDefinitionOptimizeDto expectedDecisionDefinition = createDecisionDefinitionDto();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedDecisionDefinition.getId()));
    assertThat(definitions.get(0).getDmn10Xml(), is(notNullValue()));
  }

  @Test
  public void getDecisionDefinitionXml() {
    //given
    DecisionDefinitionOptimizeDto expectedDefinitionDto = createDecisionDefinitionDto();
    addDecisionDefinitionToElasticsearch(expectedDefinitionDto);

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(expectedDefinitionDto.getKey(), expectedDefinitionDto.getVersion())
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(expectedDefinitionDto.getDmn10Xml()));
  }

  @Test
  public void getDecisionDefinitionXmlByTenant() {
    //given
    final String firstTenantId = "tenant1";
    final String secondTenantId = "tenant2";
    DecisionDefinitionOptimizeDto firstTenantDefinition = createDecisionDefinitionDto("key", firstTenantId);
    addDecisionDefinitionToElasticsearch(firstTenantDefinition);
    DecisionDefinitionOptimizeDto secondTenantDefinition = createDecisionDefinitionDto("key", secondTenantId);
    addDecisionDefinitionToElasticsearch(secondTenantDefinition);

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(
          firstTenantDefinition.getKey(), firstTenantDefinition.getVersion(), firstTenantDefinition.getTenantId()
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(firstTenantDefinition.getDmn10Xml()));
  }

  @Test
  public void getSharedDecisionDefinitionXmlByNullTenant() {
    //given
    final String firstTenantId = "tenant1";
    DecisionDefinitionOptimizeDto firstTenantDefinition = createDecisionDefinitionDto("key", firstTenantId);
    addDecisionDefinitionToElasticsearch(firstTenantDefinition);
    DecisionDefinitionOptimizeDto secondTenantDefinition = createDecisionDefinitionDto("key", null);
    addDecisionDefinitionToElasticsearch(secondTenantDefinition);

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(
          firstTenantDefinition.getKey(), firstTenantDefinition.getVersion(), null
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(secondTenantDefinition.getDmn10Xml()));
  }


  @Test
  public void getSharedDecisionDefinitionXmlByTenantWithNoSpecificDefinition() {
    //given
    final String firstTenantId = "tenant1";
    DecisionDefinitionOptimizeDto sharedDecisionDefinition = createDecisionDefinitionDto("key", null);
    addDecisionDefinitionToElasticsearch(sharedDecisionDefinition);

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(
          sharedDecisionDefinition.getKey(), sharedDecisionDefinition.getVersion(), firstTenantId
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(sharedDecisionDefinition.getDmn10Xml()));
  }

  @Test
  public void getDecisionDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule
        .getRequestExecutor()
        .withoutAuthentication()
        .buildGetDecisionDefinitionXmlRequest("foo", "bar")
        .execute();


    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDecisionDefinitionXmlWithoutAuthorization() {
    // when
    final String kermitUser = "kermit";
    final String definitionKey = "key";
    engineRule.addUser(kermitUser, kermitUser);
    engineRule.grantUserOptimizeAccess(kermitUser);
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = createDecisionDefinitionDto(definitionKey);

    Response response = embeddedOptimizeRule.getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetDecisionDefinitionXmlRequest(decisionDefinitionDto.getKey(), decisionDefinitionDto.getVersion())
      .execute();

    // then the status code is forbidden
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void getDecisionDefinitionXmlWithNonsenseVersionReturns404Code() {
    // given
    DecisionDefinitionOptimizeDto expectedDefinitionDto = createDecisionDefinitionDto();
    addDecisionDefinitionToElasticsearch(expectedDefinitionDto);

    // when
    String message =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(expectedDefinitionDto.getKey(), "nonsenseVersion")
        .execute(String.class, 404);

    // then
    assertThat(message, containsString("Could not find xml for decision definition with key"));
  }

  @Test
  public void getDecisionDefinitionXmlWithNonsenseKeyReturns404Code() {
    // given
    DecisionDefinitionOptimizeDto expectedDefinitionDto = createDecisionDefinitionDto();
    addDecisionDefinitionToElasticsearch(expectedDefinitionDto);

    // when
    String message =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest("nonsenseKey", expectedDefinitionDto.getVersion())
        .execute(String.class, 404);

    // then
    assertThat(message, containsString("Could not find xml for decision definition with key"));
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants() {
    //given
    final String tenantId1 = "tenant1";
    final String tenantName1 = "Tenant 1";
    final String tenantId2 = "tenant2";
    final String tenantName2 = "Tenant 2";
    createTenant(tenantId1, tenantName1);
    createTenant(tenantId2, tenantName2);
    final String decDefKey1 = "decDefKey1";
    final String decDefKey2 = "decDefKey2";
    createDecisionDefinitionsForKey(decDefKey1, 3);
    createDecisionDefinitionsForKey(decDefKey2, 2, tenantId1);
    createDecisionDefinitionsForKey(decDefKey2, 3, tenantId2);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(2));

    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(decDefKey1));
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
    assertThat(secondDefinition.getKey(), is(decDefKey2));
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
  public void testGetDecisionDefinitionVersionsWithTenants_sharedAndTenantDefinitionWithSameKeyAndVersion() {
    //given
    final String tenantId1 = "tenant1";
    final String tenantName1 = "Tenant 1";
    createTenant(tenantId1, tenantName1);
    final String decDefKey1 = "decDefKey1";

    createDecisionDefinitionsForKey(decDefKey1, 2);
    createDecisionDefinitionsForKey(decDefKey1, 3, tenantId1);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));

    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(decDefKey1));
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
  public void testGetDecisionDefinitionVersionsWithTenants_onlyAuthorizedTenantsAvailable() {
    // given
    final String tenantId1 = "tenant1";
    final String tenantName1 = "Tenant 1";
    final String tenantId2 = "2";
    final String tenantName2 = "My Tenant 2";
    createTenant(tenantId1, tenantName1);
    createTenant(tenantId2, tenantName2);
    final String decDefKey = "decDefKey";

    createDecisionDefinitionsForKey(decDefKey, 2, tenantId1);
    createDecisionDefinitionsForKey(decDefKey, 3, tenantId2);

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), tenantId1);
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, decDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(decDefKey));
    final List<DefinitionVersionsRestDto> definitionVersions = availableDefinition.getVersions();
    assertThat(definitionVersions.size(), is(2));
    final List<TenantRestDto> tenantsAvailableOnFirstDefinition =
      ImmutableList.of(new TenantRestDto(tenantId1, tenantName1));
    assertThat(availableDefinition.getTenants().size(), is(1));
    assertThat(availableDefinition.getTenants(), is(tenantsAvailableOnFirstDefinition));
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants_sharedDefinitionNoneTenantAndAuthorizedTenantsAvailable() {
    // given
    final String tenantId1 = "1";
    final String tenantName1 = "My Tenant 1";
    final String tenantId2 = "2";
    final String tenantName2 = "My Tenant 2";

    createTenant(tenantId1, tenantName1);
    createTenant(tenantId2, tenantName2);
    final String decisionDefKey = "decisionDefKey";
    createDecisionDefinitionsForKey(decisionDefKey, 4);
    createDecisionDefinitionsForKey(decisionDefKey, 3, tenantId2);

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), tenantId1);
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, decisionDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));

    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(decisionDefKey));
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
    authorizationDto.setResourceType(RESOURCE_TYPE_DECISION_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private void createDecisionDefinitionsForKey(String key, int count) {
    createDecisionDefinitionsForKey(key, count, null);
  }

  private void createDecisionDefinitionsForKey(String key, int count, String tenantId) {
    IntStream.range(0, count).forEach(
      i -> createDecisionDefinitionDto(key, String.valueOf(i), tenantId)
    );
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto() {
    return createDecisionDefinitionDto("key", "1", null);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(final String key) {
    return createDecisionDefinitionDto(key, null);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, final String tenantId) {
    return createDecisionDefinitionDto(key, "1", tenantId);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, String version,final String tenantId) {
    DecisionDefinitionOptimizeDto decisionDefinitionDto = new DecisionDefinitionOptimizeDto()
      .setId("id-" + key + "-version-" + version + "-" + tenantId)
      .setKey(key)
      .setVersion(version)
      .setVersionTag(VERSION_TAG)
      .setTenantId(tenantId)
      .setEngine(DEFAULT_ENGINE_ALIAS)
      .setDmn10Xml("id-" + key + "-version-" + version + "-" + tenantId);
    addDecisionDefinitionToElasticsearch(decisionDefinitionDto);
    return decisionDefinitionDto;
  }

  private void addDecisionDefinitionToElasticsearch(final DecisionDefinitionOptimizeDto definitionOptimizeDto) {
    elasticSearchRule.addEntryToElasticsearch(
      DECISION_DEFINITION_TYPE, definitionOptimizeDto.getId(), definitionOptimizeDto
    );
  }

}
