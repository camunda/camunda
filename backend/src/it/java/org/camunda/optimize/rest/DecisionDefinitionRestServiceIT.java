/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DecisionDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsRestDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_TYPE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class DecisionDefinitionRestServiceIT {
  private static final String TENANT_NONE_NAME = TenantService.TENANT_NONE.getName();

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
  public void testGetDecisionDefinitionsGroupedByKey() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    createDecisionDefinitionsForKey(key1, 11);
    createDecisionDefinitionsForKey(key2, 2);

    // when
    List<DecisionDefinitionGroupOptimizeDto> actual = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionDefinitionsGroupedByKeyRequest()
      .executeAndReturnList(DecisionDefinitionGroupOptimizeDto.class, 200);

    // then
    assertThat(actual, is(notNullValue()));
    assertThat(actual.size(), is(2));
    // assert that key1 comes first in list
    actual.sort(Comparator.comparing(
      DecisionDefinitionGroupOptimizeDto::getVersions,
      Comparator.comparing(v -> v.get(0).getKey())
    ));
    DecisionDefinitionGroupOptimizeDto decisionGroup1 = actual.get(0);
    assertThat(decisionGroup1.getKey(), is(key1));
    assertThat(decisionGroup1.getVersions().size(), is(11));
    assertThat(decisionGroup1.getVersions().get(0).getVersion(), is("10"));
    assertThat(decisionGroup1.getVersions().get(1).getVersion(), is("9"));
    assertThat(decisionGroup1.getVersions().get(2).getVersion(), is("8"));
    DecisionDefinitionGroupOptimizeDto decisionGroup2 = actual.get(1);
    assertThat(decisionGroup2.getKey(), is(key2));
    assertThat(decisionGroup2.getVersions().size(), is(2));
    assertThat(decisionGroup2.getVersions().get(0).getVersion(), is("1"));
    assertThat(decisionGroup2.getVersions().get(1).getVersion(), is("0"));
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
    final List<DefinitionWithTenantsRestDto> firstDefinitionVersions = firstDefinition.getVersions();
    assertThat(firstDefinitionVersions.size(), is(4));
    assertThat(firstDefinitionVersions.get(0).getVersion(), is(ALL_VERSIONS));
    final List<TenantRestDto> tenantsAvailableOnFirstDefinition = firstDefinitionVersions.get(0).getTenants();
    assertThat(tenantsAvailableOnFirstDefinition.size(), is(3));
    assertThat(tenantsAvailableOnFirstDefinition.get(0).getId(), is(nullValue()));
    assertThat(tenantsAvailableOnFirstDefinition.get(0).getName(), is(TENANT_NONE_NAME));
    assertThat(tenantsAvailableOnFirstDefinition.get(1).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnFirstDefinition.get(1).getName(), is(tenantName1));
    assertThat(tenantsAvailableOnFirstDefinition.get(2).getId(), is(tenantId2));
    assertThat(tenantsAvailableOnFirstDefinition.get(2).getName(), is(tenantName2));
    assertThat(firstDefinitionVersions.get(1).getVersion(), is("2"));
    assertThat(firstDefinitionVersions.get(1).getTenants(), is(tenantsAvailableOnFirstDefinition));
    assertThat(firstDefinitionVersions.get(2).getVersion(), is("1"));
    assertThat(firstDefinitionVersions.get(2).getTenants(), is(tenantsAvailableOnFirstDefinition));
    assertThat(firstDefinitionVersions.get(3).getVersion(), is("0"));
    assertThat(firstDefinitionVersions.get(3).getTenants(), is(tenantsAvailableOnFirstDefinition));

    final DefinitionVersionsWithTenantsRestDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey(), is(decDefKey2));
    final List<DefinitionWithTenantsRestDto> secondDefinitionVersions = secondDefinition.getVersions();
    assertThat(secondDefinitionVersions.size(), is(4));
    assertThat(firstDefinitionVersions.get(0).getVersion(), is(ALL_VERSIONS));
    final List<TenantRestDto> tenantsAvailableOnSecondDefinitionVersionAll = secondDefinitionVersions.get(0).getTenants();
    assertThat(tenantsAvailableOnSecondDefinitionVersionAll.size(), is(2));
    assertThat(tenantsAvailableOnSecondDefinitionVersionAll.get(0).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnSecondDefinitionVersionAll.get(0).getName(), is(tenantName1));
    assertThat(tenantsAvailableOnSecondDefinitionVersionAll.get(1).getId(), is(tenantId2));
    assertThat(tenantsAvailableOnSecondDefinitionVersionAll.get(1).getName(), is(tenantName2));
    assertThat(secondDefinitionVersions.get(2).getVersion(), is("1"));
    assertThat(secondDefinitionVersions.get(1).getVersion(), is("2"));
    final List<TenantRestDto> tenantsAvailableOnSecondDefinitionVersion2 = secondDefinitionVersions.get(1).getTenants();
    assertThat(tenantsAvailableOnSecondDefinitionVersion2.size(), is(1));
    assertThat(tenantsAvailableOnSecondDefinitionVersion2.get(0).getId(), is(tenantId2));
    assertThat(tenantsAvailableOnSecondDefinitionVersion2.get(0).getName(), is(tenantName2));
    assertThat(secondDefinitionVersions.get(2).getVersion(), is("1"));
    final List<TenantRestDto> tenantsAvailableOnSecondDefinitionVersion1 = secondDefinitionVersions.get(2).getTenants();
    assertThat(tenantsAvailableOnSecondDefinitionVersion1.size(), is(2));
    assertThat(tenantsAvailableOnSecondDefinitionVersion1.get(0).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnSecondDefinitionVersion1.get(0).getName(), is(tenantName1));
    assertThat(tenantsAvailableOnSecondDefinitionVersion1.get(1).getId(), is(tenantId2));
    assertThat(tenantsAvailableOnSecondDefinitionVersion1.get(1).getName(), is(tenantName2));
    assertThat(secondDefinitionVersions.get(3).getVersion(), is("0"));
    final List<TenantRestDto> tenantsAvailableOnSecondDefinitionVersion0 = secondDefinitionVersions.get(3).getTenants();
    assertThat(tenantsAvailableOnSecondDefinitionVersion0.size(), is(2));
    assertThat(tenantsAvailableOnSecondDefinitionVersion0.get(0).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnSecondDefinitionVersion0.get(0).getName(), is(tenantName1));
    assertThat(tenantsAvailableOnSecondDefinitionVersion0.get(1).getId(), is(tenantId2));
    assertThat(tenantsAvailableOnSecondDefinitionVersion0.get(1).getName(), is(tenantName2));
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
    final List<DefinitionWithTenantsRestDto> firstDefinitionVersions = firstDefinition.getVersions();
    assertThat(firstDefinitionVersions.size(), is(4));
    assertThat(firstDefinitionVersions.get(0).getVersion(), is(ALL_VERSIONS));
    final List<TenantRestDto> tenantsAvailableOnVersionAll = firstDefinitionVersions.get(0).getTenants();
    assertThat(tenantsAvailableOnVersionAll.size(), is(2));
    assertThat(tenantsAvailableOnVersionAll.get(0).getId(), is(nullValue()));
    assertThat(tenantsAvailableOnVersionAll.get(0).getName(), is(TENANT_NONE_NAME));
    assertThat(tenantsAvailableOnVersionAll.get(1).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnVersionAll.get(1).getName(), is(tenantName1));
    assertThat(firstDefinitionVersions.get(1).getVersion(), is("2"));
    final List<TenantRestDto> tenantsAvailableOnVersion2 = firstDefinitionVersions.get(1).getTenants();
    assertThat(tenantsAvailableOnVersion2.size(), is(1));
    assertThat(tenantsAvailableOnVersion2.get(0).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnVersion2.get(0).getName(), is(tenantName1));
    assertThat(firstDefinitionVersions.get(2).getVersion(), is("1"));
    final List<TenantRestDto> tenantsAvailableOnVersion1 = firstDefinitionVersions.get(2).getTenants();
    assertThat(tenantsAvailableOnVersion1.size(), is(2));
    assertThat(tenantsAvailableOnVersion1.get(0).getId(), is(nullValue()));
    assertThat(tenantsAvailableOnVersion1.get(0).getName(), is(TENANT_NONE_NAME));
    assertThat(tenantsAvailableOnVersion1.get(1).getId(), is(tenantId1));
    assertThat(tenantsAvailableOnVersion1.get(1).getName(), is(tenantName1));
    assertThat(firstDefinitionVersions.get(3).getVersion(), is("0"));
    assertThat(firstDefinitionVersions.get(3).getTenants(), is(tenantsAvailableOnVersion1));
  }

  private void createTenant(final String id, final String name) {
    final TenantDto tenantDto = new TenantDto(id, name, "engine");
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
    return createDecisionDefinitionDto(key, "1");
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, final String tenantId) {
    return createDecisionDefinitionDto(key, "1", tenantId);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, String version,final String tenantId) {
    DecisionDefinitionOptimizeDto decisionDefinitionDto = new DecisionDefinitionOptimizeDto()
      .setId("id-" + key + "-version-" + version + "-" + tenantId)
      .setKey(key)
      .setVersion(version)
      .setTenantId(tenantId)
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
