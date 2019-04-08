/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DecisionDefinitionGroupOptimizeDto;
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

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class DecisionDefinitionRestServiceIT {
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
    elasticSearchRule.addEntryToElasticsearch(
      DECISION_DEFINITION_TYPE,
      expectedDefinitionDto.getId(),
      expectedDefinitionDto
    );

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
    elasticSearchRule.addEntryToElasticsearch(
      DECISION_DEFINITION_TYPE,
      expectedDefinitionDto.getId(),
      expectedDefinitionDto
    );

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
    elasticSearchRule.addEntryToElasticsearch(
      DECISION_DEFINITION_TYPE,
      expectedDefinitionDto.getId(),
      expectedDefinitionDto
    );

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
    IntStream.range(0, count).forEach(
      i -> createDecisionDefinitionDto(key, String.valueOf(i))
    );
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto() {
    return createDecisionDefinitionDto("key", "1");
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key) {
    return createDecisionDefinitionDto(key, "1");
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key, String version) {
    DecisionDefinitionOptimizeDto decisionDefinitionDto = new DecisionDefinitionOptimizeDto();
    decisionDefinitionDto.setDmn10Xml("DecisionModelXml");
    decisionDefinitionDto.setKey(key);
    decisionDefinitionDto.setVersion(version);
    decisionDefinitionDto.setId("id-" + key + "-version-" + version);
    elasticSearchRule.addEntryToElasticsearch(
      DECISION_DEFINITION_TYPE, decisionDefinitionDto.getId(), decisionDefinitionDto
    );
    return decisionDefinitionDto;
  }

}
