/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;

public class DecisionDefinitionRestServiceIT extends AbstractDefinitionRestServiceIT {

  private static final String ALL_VERSIONS_STRING = "ALL";

  @Test
  public void getDecisionDefinitions() {
    // given
    final DecisionDefinitionOptimizeDto expectedDecisionDefinition = createDecisionDefinitionDto();

    // when
    List<DecisionDefinitionOptimizeDto> definitions = definitionClient.getAllDecisionDefinitions();

    // then
    assertThat(definitions).isNotNull();
    assertThat(definitions.get(0).getId()).isEqualTo(expectedDecisionDefinition.getId());
  }

  @Test
  public void getDecisionDefinitionsExcludesDeletedDefinitions() {
    // given
    final DecisionDefinitionOptimizeDto nonDeletedDefinition =
      createDecisionDefinitionDto("someKey", "1", null, "not deleted", false);
    // the deleted definition should not appear in the results
    createDecisionDefinitionDto("someKey", "1", null, "deleted", true);

    // when
    List<DecisionDefinitionOptimizeDto> definitions = definitionClient.getAllDecisionDefinitions();

    // then
    assertThat(definitions)
      .isNotNull()
      .singleElement()
      .satisfies(returnedDef -> {
        assertThat(returnedDef.getKey()).isEqualTo(nonDeletedDefinition.getKey());
        assertThat(returnedDef.isDeleted()).isFalse();
      });
  }

  @Test
  public void getDecisionDefinitionsReturnOnlyThoseAuthorizedToSee() {
    // given
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey = "access";
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, authorizedDefinitionKey);

    final DecisionDefinitionOptimizeDto authorizedToSee = createDecisionDefinitionDto(authorizedDefinitionKey);
    createDecisionDefinitionDto(notAuthorizedDefinitionKey);

    // when
    List<DecisionDefinitionOptimizeDto> definitions = definitionClient.getAllDecisionDefinitionsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then we only get 1 definition, the one kermit is authorized to see
    assertThat(definitions).isNotNull().hasSize(1);
    assertThat(definitions.get(0).getId()).isEqualTo(authorizedToSee.getId());
  }

  @Test
  public void getDecisionDefinitionsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDecisionDefinitionsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDecisionDefinitionsWithXml() {
    // given
    final DecisionDefinitionOptimizeDto expectedDecisionDefinition = createDecisionDefinitionDto();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).isNotNull();
    assertThat(definitions.get(0).getId()).isEqualTo(expectedDecisionDefinition.getId());
    assertThat(definitions.get(0).getDmn10Xml()).isNotNull();
  }

  @Test
  public void getDecisionDefinitionXml() {
    // given
    DecisionDefinitionOptimizeDto expectedDefinitionDto = createDecisionDefinitionDto();
    addDecisionDefinitionToElasticsearch(expectedDefinitionDto);

    // when
    String actualXml = definitionClient.getDecisionDefinitionXml(
      expectedDefinitionDto.getKey(),
      expectedDefinitionDto.getVersion()
    );

    // then
    assertThat(actualXml).isEqualTo(expectedDefinitionDto.getDmn10Xml());
  }

  @Test
  public void getLatestDecisionDefinitionXml() {
    // given
    final String key = "aKey";
    DecisionDefinitionOptimizeDto expectedDto1 = createDecisionDefinitionDto(key, "1", null);
    DecisionDefinitionOptimizeDto expectedDto2 = createDecisionDefinitionDto(key, "2", null);
    addDecisionDefinitionToElasticsearch(expectedDto1);
    addDecisionDefinitionToElasticsearch(expectedDto2);

    // when
    String actualXml =
      definitionClient.getDecisionDefinitionXml(key, ALL_VERSIONS_STRING);

    // then
    assertThat(actualXml).isEqualTo(expectedDto2.getDmn10Xml());
  }

  @Test
  public void getDecisionDefinitionXmlByTenant() {
    // given
    final String firstTenantId = "tenant1";
    final String secondTenantId = "tenant2";
    DecisionDefinitionOptimizeDto firstTenantDefinition = createDecisionDefinitionDto("key", firstTenantId);
    addDecisionDefinitionToElasticsearch(firstTenantDefinition);
    DecisionDefinitionOptimizeDto secondTenantDefinition = createDecisionDefinitionDto("key", secondTenantId);
    addDecisionDefinitionToElasticsearch(secondTenantDefinition);

    // when
    String actualXml = definitionClient.getDecisionDefinitionXml(
      firstTenantDefinition.getKey(), firstTenantDefinition.getVersion(), firstTenantDefinition.getTenantId()
    );

    // then
    assertThat(actualXml).isEqualTo(firstTenantDefinition.getDmn10Xml());
  }

  @Test
  public void getSharedDecisionDefinitionXmlByNullTenant() {
    // given
    final String firstTenantId = "tenant1";
    DecisionDefinitionOptimizeDto firstTenantDefinition = createDecisionDefinitionDto("key", firstTenantId);
    addDecisionDefinitionToElasticsearch(firstTenantDefinition);
    DecisionDefinitionOptimizeDto secondTenantDefinition = createDecisionDefinitionDto("key", null);
    addDecisionDefinitionToElasticsearch(secondTenantDefinition);

    // when
    String actualXml = definitionClient.getDecisionDefinitionXml(
      firstTenantDefinition.getKey(),
      firstTenantDefinition.getVersion()
    );
    // then
    assertThat(actualXml).isEqualTo(secondTenantDefinition.getDmn10Xml());
  }

  @Test
  public void getSharedDecisionDefinitionXmlByTenantWithNoSpecificDefinition() {
    // given
    final String firstTenantId = "tenant1";
    DecisionDefinitionOptimizeDto sharedDecisionDefinition = createDecisionDefinitionDto("key", null);
    addDecisionDefinitionToElasticsearch(sharedDecisionDefinition);

    // when
    String actualXml = definitionClient.getDecisionDefinitionXml(
      sharedDecisionDefinition.getKey(),
      sharedDecisionDefinition.getVersion(),
      firstTenantId
    );

    // then
    assertThat(actualXml).isEqualTo(sharedDecisionDefinition.getDmn10Xml());
  }

  @Test
  public void getDecisionDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .withoutAuthentication()
        .buildGetDecisionDefinitionXmlRequest("foo", "bar")
        .execute();


    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDecisionDefinitionXmlWithoutAuthorization() {
    // when
    final String kermitUser = "kermit";
    final String definitionKey = "key";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = createDecisionDefinitionDto(definitionKey);

    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetDecisionDefinitionXmlRequest(decisionDefinitionDto.getKey(), decisionDefinitionDto.getVersion())
      .execute();

    // then the status code is forbidden
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getDecisionDefinitionXmlWithNonsenseVersionReturns404Code() {
    // given
    DecisionDefinitionOptimizeDto expectedDefinitionDto = createDecisionDefinitionDto();
    addDecisionDefinitionToElasticsearch(expectedDefinitionDto);

    // when
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(expectedDefinitionDto.getKey(), "nonsenseVersion")
        .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(message).contains(EXPECTED_DEFINITION_NOT_FOUND_MESSAGE);
  }

  @Test
  public void getDecisionDefinitionXmlWithNonsenseKeyReturns404Code() {
    // given
    DecisionDefinitionOptimizeDto expectedDefinitionDto = createDecisionDefinitionDto();
    addDecisionDefinitionToElasticsearch(expectedDefinitionDto);

    // when
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest("nonsenseKey", expectedDefinitionDto.getVersion())
        .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(message).contains(EXPECTED_DEFINITION_NOT_FOUND_MESSAGE);
  }

  @Override
  protected int getDefinitionResourceType() {
    return RESOURCE_TYPE_DECISION_DEFINITION;
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto() {
    return createDecisionDefinitionDto("key", "1", null);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(final String key) {
    return createDecisionDefinitionDto(key, null);
  }


  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(final String key, final String tenantId) {
    return createDecisionDefinitionDto(key, "1", tenantId);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(final String key,
                                                                    final String version,
                                                                    final String tenantId) {
    return createDecisionDefinitionDto(key, version, tenantId, null);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(final String key,
                                                                    final String version,
                                                                    final String tenantId,
                                                                    final String name) {
    return createDecisionDefinitionDto(key, version, tenantId, name, false);
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(final String key,
                                                                    final String version,
                                                                    final String tenantId,
                                                                    final String name,
                                                                    final boolean deleted) {
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = DecisionDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(key)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .deleted(deleted)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .name(name)
      .dmn10Xml("id-" + key + "-version-" + version + "-" + tenantId)
      .build();
    addDecisionDefinitionToElasticsearch(decisionDefinitionDto);
    return decisionDefinitionDto;
  }

  private void addDecisionDefinitionToElasticsearch(final DecisionDefinitionOptimizeDto definitionOptimizeDto) {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME, definitionOptimizeDto.getId(), definitionOptimizeDto
    );
  }

}
