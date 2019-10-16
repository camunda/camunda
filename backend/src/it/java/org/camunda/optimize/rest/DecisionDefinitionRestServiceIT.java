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
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionWithTenantsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class DecisionDefinitionRestServiceIT {
  private static final String VERSION_TAG = "aVersionTag";
  private static final String TENANT_NONE_NAME = TenantService.TENANT_NOT_DEFINED.getName();
  private static final TenantRestDto TENANT_NONE_DTO = new TenantRestDto(null, TENANT_NONE_NAME);
  private static final TenantRestDto TENANT_1_DTO = new TenantRestDto("tenant1", "Tenant 1");
  private static final TenantRestDto TENANT_2_DTO = new TenantRestDto("tenant2", "Tenant 2");

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void getDecisionDefinitions() {
    //given
    final DecisionDefinitionOptimizeDto expectedDecisionDefinition = createDecisionDefinitionDto();

    // when
    List<DecisionDefinitionOptimizeDto> definitions = embeddedOptimizeExtensionRule
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
    engineIntegrationExtensionRule.addUser(kermitUser, kermitUser);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);

    final DecisionDefinitionOptimizeDto authorizedToSee = createDecisionDefinitionDto(authorizedDefinitionKey);
    final DecisionDefinitionOptimizeDto notAuthorizedToSee = createDecisionDefinitionDto(notAuthorizedDefinitionKey);

    // when
    List<DecisionDefinitionOptimizeDto> definitions = embeddedOptimizeExtensionRule
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
    Response response = embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
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
    engineIntegrationExtensionRule.addUser(kermitUser, kermitUser);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(kermitUser);
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = createDecisionDefinitionDto(definitionKey);

    Response response = embeddedOptimizeExtensionRule.getRequestExecutor()
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
      embeddedOptimizeExtensionRule
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
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest("nonsenseKey", expectedDefinitionDto.getVersion())
        .execute(String.class, 404);

    // then
    assertThat(message, containsString("Could not find xml for decision definition with key"));
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants() {
    //given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String decDefKey1 = "decDefKey1";
    final String decDefKey2 = "decDefKey2";
    createDecisionDefinitionsForKey(decDefKey1, 3);
    createDecisionDefinitionsForKey(decDefKey2, 2, TENANT_1_DTO.getId());
    createDecisionDefinitionsForKey(decDefKey2, 3, TENANT_2_DTO.getId());

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(2));
    // first definition
    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(decDefKey1));
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
    assertThat(secondDefinition.getKey(), is(decDefKey2));
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
  public void testDefinitionSorting() {
    createDecisionDefinitionDto("z", "1", null, "a");
    createDecisionDefinitionDto("x", "1", null, "b");
    createDecisionDefinitionsForKey("c", 1);
    createDecisionDefinitionsForKey("D", 1);
    createDecisionDefinitionsForKey("e", 1);
    createDecisionDefinitionsForKey("F", 1);


    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    assertThat(definitions.get(0).getKey(), is("z"));
    assertThat(definitions.get(1).getKey(), is("x"));
    assertThat(definitions.get(2).getKey(), is("c"));
    assertThat(definitions.get(3).getKey(), is("D"));
    assertThat(definitions.get(4).getKey(), is("e"));
    assertThat(definitions.get(5).getKey(), is("F"));
  }

  @Test
  public void testGetDecisionDefinitionVersionsWithTenants_sharedAndTenantDefinitionWithSameKeyAndVersion() {
    //given
    createTenant(TENANT_1_DTO);
    final String decDefKey1 = "decDefKey1";

    createDecisionDefinitionsForKey(decDefKey1, 2);
    createDecisionDefinitionsForKey(decDefKey1, 3, TENANT_1_DTO.getId());

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(decDefKey1));
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
    final String decDefKey = "decDefKey";

    createDecisionDefinitionsForKey(decDefKey, 2, TENANT_1_DTO.getId());
    createDecisionDefinitionsForKey(decDefKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, decDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(decDefKey));
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
    final String decisionDefKey = "decisionDefKey";
    createDecisionDefinitionsForKey(decisionDefKey, 4);
    createDecisionDefinitionsForKey(decisionDefKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, decisionDefKey);

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(tenant1UserId, tenant1UserId)
      .buildGetDecisionDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(decisionDefKey));
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
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
  }

  private void createOptimizeUser(final String tenantUser) {
    engineIntegrationExtensionRule.addUser(tenantUser, tenantUser);
    engineIntegrationExtensionRule.grantUserOptimizeAccess(tenantUser);
  }

  private void createTenant(final TenantRestDto tenantRestDto) {
    createTenant(tenantRestDto.getId(), tenantRestDto.getName());
  }

  private void createTenant(final String id, final String name) {
    final TenantDto tenantDto = new TenantDto(id, name, DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(TENANT_INDEX_NAME, id, tenantDto);
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_DECISION_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
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

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, String version, final String tenantId) {
    return createDecisionDefinitionDto(key, version, tenantId, null);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, String version, final String tenantId
    , String name) {
    DecisionDefinitionOptimizeDto decisionDefinitionDto = new DecisionDefinitionOptimizeDto()
      .setId("id-" + key + "-version-" + version + "-" + tenantId)
      .setKey(key)
      .setVersion(version)
      .setVersionTag(VERSION_TAG)
      .setTenantId(tenantId)
      .setEngine(DEFAULT_ENGINE_ALIAS)
      .setName(name)
      .setDmn10Xml("id-" + key + "-version-" + version + "-" + tenantId);
    addDecisionDefinitionToElasticsearch(decisionDefinitionDto);
    return decisionDefinitionDto;
  }

  private void addDecisionDefinitionToElasticsearch(final DecisionDefinitionOptimizeDto definitionOptimizeDto) {
    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME, definitionOptimizeDto.getId(), definitionOptimizeDto
    );
  }

}
