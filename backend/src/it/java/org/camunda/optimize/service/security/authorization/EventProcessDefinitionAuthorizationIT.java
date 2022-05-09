/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.Lists;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class EventProcessDefinitionAuthorizationIT extends AbstractIT {

  private static final String EVENT_PROCESS_DEFINITION_VERSION = "1";

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @Test
  public void getDefinitions_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new GroupDto(GROUP_ID)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new GroupDto("otherGroup")
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitionsAsUser(
      KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitions).extracting(DefinitionResponseDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitions_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new UserDto(KERMIT_USER)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitionsAsUser(
      KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitions).extracting(DefinitionResponseDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitions_engineUserGrantForKeyDoesNotGrantEventProcessAccess() {
    // given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey,
      new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitionsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(definitions).isEmpty();
  }

  @Test
  public void getDefinitions_engineGroupGrantForKeyDoesNotGrantEventProcessAccess() {
    // given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermitGroup(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey, new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitionsAsUser(
      KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitions).isEmpty();
  }

  @Test
  public void getDefinitions_engineUserRevokeForKeyDoesNotRevokeEventProcessAccess() {
    // given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey, new UserDto(KERMIT_USER)
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitionsAsUser(
      KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitions).extracting(DefinitionResponseDto::getKey).containsExactly(definitionKey);
  }

  @Test
  public void getDefinitions_engineGroupRevokeForKeyDoesNotGrantEventProcessAccess() {
    // given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION
    );

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey, new UserDto(KERMIT_USER)
    );

    // when
    final List<DefinitionResponseDto> definitions = definitionClient.getAllDefinitionsAsUser(
      KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitions).extracting(DefinitionResponseDto::getKey).containsExactly(definitionKey);
  }

  @Test
  public void getProcessDefinitions_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new GroupDto(GROUP_ID)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new GroupDto("otherGroup")
    );

    // when
    final List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitionsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(definitions).extracting(ProcessDefinitionOptimizeDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getProcessDefinitions_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new UserDto(KERMIT_USER)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitionsAsUser(
      KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(definitions).extracting(ProcessDefinitionOptimizeDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getProcessDefinitionByTypeAndKey_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new GroupDto(GROUP_ID)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new GroupDto("otherGroup")
    );

    // when
    final ProcessDefinitionOptimizeDto definition1 = getProcessDefinitionByKeyAsUser(definitionKey1, KERMIT_USER);
    final Response definition2Response = executeGetProcessDefinitionByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definition1).extracting(ProcessDefinitionOptimizeDto::getKey).isEqualTo(definitionKey1);

    assertThat(definition2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getProcessDefinitionByTypeAndKey_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new UserDto(KERMIT_USER)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new UserDto(DEFAULT_USERNAME)
    );

    // when
    final ProcessDefinitionOptimizeDto definition = getProcessDefinitionByKeyAsUser(definitionKey1, KERMIT_USER);
    final Response definition2Response = executeGetProcessDefinitionByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definition).extracting(ProcessDefinitionOptimizeDto::getKey).isEqualTo(definitionKey1);

    assertThat(definition2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getDefinitionKeysByType_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new GroupDto(GROUP_ID)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new GroupDto("otherGroup")
    );

    // when
    final List<DefinitionKeyResponseDto> keys = definitionClient.getDefinitionKeysByTypeAsUser(
      DefinitionType.PROCESS, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(keys).extracting(DefinitionKeyResponseDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitionKeysByType_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new UserDto(KERMIT_USER)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<DefinitionKeyResponseDto> keys = definitionClient.getDefinitionKeysByTypeAsUser(
      DefinitionType.PROCESS, KERMIT_USER, KERMIT_USER
    );

    // then
    assertThat(keys).extracting(DefinitionKeyResponseDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitionVersionsByKeyByType_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new GroupDto(GROUP_ID)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new GroupDto("otherGroup")
    );

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKeyAsUser(
      DefinitionType.PROCESS, definitionKey1, KERMIT_USER, KERMIT_USER
    );

    final Response unauthorizedKeyResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(DefinitionType.PROCESS.getId(), definitionKey2)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();


    // then
    assertThat(versions).extracting(DefinitionVersionResponseDto::getVersion).containsExactly("1");

    assertThat(unauthorizedKeyResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getDefinitionVersionsByKeyByType_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1, new UserDto(KERMIT_USER)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2, new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<DefinitionVersionResponseDto> versions = definitionClient.getDefinitionVersionsByTypeAndKeyAsUser(
      DefinitionType.PROCESS, definitionKey1, KERMIT_USER, KERMIT_USER
    );

    final Response unauthorizedKeyResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(DefinitionType.PROCESS.getId(), definitionKey2)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(versions).extracting(DefinitionVersionResponseDto::getVersion).containsExactly("1");
    assertThat(unauthorizedKeyResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getDefinitionsGroupedByTenant_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1,
      new GroupDto(GROUP_ID)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2,
      new GroupDto("otherGroup")
    );

    // when
    final List<TenantWithDefinitionsResponseDto> definitionsWithVersionsAndTenants =
      definitionClient.getDefinitionsGroupedByTenantAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(definitionsWithVersionsAndTenants)
      .hasSize(1)
      .singleElement()
      .satisfies(tenantWithDefinitionsDto -> {
        assertThat(tenantWithDefinitionsDto)
          .hasFieldOrPropertyWithValue("id", null);
        assertThat(tenantWithDefinitionsDto.getDefinitions())
          .extracting(SimpleDefinitionDto::getKey)
          .containsExactly(definitionKey1);
      });
  }

  @Test
  public void getDefinitionsGroupedByTenant_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey1,
      new UserDto(KERMIT_USER)
    );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2,
      new UserDto(DEFAULT_USERNAME)
    );

    // when
    final List<TenantWithDefinitionsResponseDto> definitionsWithVersionsAndTenants =
      definitionClient.getDefinitionsGroupedByTenantAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(definitionsWithVersionsAndTenants)
      .hasSize(1)
      .singleElement()
      .satisfies(tenantWithDefinitionsDto -> {
        assertThat(tenantWithDefinitionsDto)
          .hasFieldOrPropertyWithValue("id", null);
        assertThat(tenantWithDefinitionsDto.getDefinitions())
          .extracting(SimpleDefinitionDto::getKey)
          .containsExactly(definitionKey1);
      });
  }

  @Test
  public void getProcessDefinitionXml_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final EventProcessDefinitionDto eventProcessDefinition1 =
      elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
        definitionKey1,
        new GroupDto(GROUP_ID)
      );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2,
      new GroupDto("otherGroup")
    );

    // when
    final String definitionXml1 = definitionClient.getProcessDefinitionXmlAsUser(
      definitionKey1,
      EVENT_PROCESS_DEFINITION_VERSION,
      null,
      KERMIT_USER,
      KERMIT_USER
    );
    final Response definitionXml2Response = executeGetProcessDefinitionXmlByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definitionXml1).isEqualTo(eventProcessDefinition1.getBpmn20Xml());

    assertThat(definitionXml2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getProcessDefinitionXml_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final EventProcessDefinitionDto eventProcessDefinition1 =
      elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
        definitionKey1,
        new UserDto(KERMIT_USER)
      );
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      definitionKey2,
      new UserDto(DEFAULT_USERNAME)
    );

    // when
    final String definitionXml1 = definitionClient.getProcessDefinitionXmlAsUser(
      definitionKey1,
      EVENT_PROCESS_DEFINITION_VERSION,
      null,
      KERMIT_USER,
      KERMIT_USER
    );
    final Response definitionXml2Response = executeGetProcessDefinitionXmlByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definitionXml1).isEqualTo(eventProcessDefinition1.getBpmn20Xml());

    assertThat(definitionXml2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private ProcessDefinitionOptimizeDto getProcessDefinitionByKeyAsUser(final String key, final String user) {
    return executeGetProcessDefinitionByKeyAsUser(key, user).readEntity(ProcessDefinitionOptimizeDto.class);
  }

  private Response executeGetProcessDefinitionByKeyAsUser(final String definitionKey2, final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionByKeyRequest(definitionKey2)
      .withUserAuthentication(user, user)
      .execute();
  }

  private Response executeGetProcessDefinitionXmlByKeyAsUser(final String definitionKey, final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(definitionKey, EVENT_PROCESS_DEFINITION_VERSION)
      .withUserAuthentication(user, user)
      .execute();
  }
}

