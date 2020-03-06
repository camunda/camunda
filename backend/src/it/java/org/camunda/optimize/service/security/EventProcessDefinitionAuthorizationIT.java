/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessMappingDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;

public class EventProcessDefinitionAuthorizationIT extends AbstractIT {

  private static final String XML_VALUE = "<xml></xml>";
  private static final String EVENT_PROCESS_DEFINITION_VERSION = "1";

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @Test
  public void getDefinitions_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new GroupDto(GROUP_ID));
    addSimpleEventProcessToElasticsearch(definitionKey2, new GroupDto("otherGroup"));

    // when
    final List<DefinitionWithTenantsDto> definitions = getDefinitionsAsUser(KERMIT_USER);

    // then
    assertThat(definitions).extracting(DefinitionWithTenantsDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitions_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new UserDto(KERMIT_USER));
    addSimpleEventProcessToElasticsearch(definitionKey2, new UserDto(DEFAULT_USERNAME));

    // when
    final List<DefinitionWithTenantsDto> definitions = getDefinitionsAsUser(KERMIT_USER);

    // then
    assertThat(definitions).extracting(DefinitionWithTenantsDto::getKey).containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitions_engineUserGrantForKeyDoesNotGrantEventProcessAccess() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey, new UserDto(DEFAULT_USERNAME));

    // when
    final List<DefinitionWithTenantsDto> definitions = getDefinitionsAsUser(KERMIT_USER);

    // then
    assertThat(definitions).isEmpty();
  }

  @Test
  public void getDefinitions_engineGroupGrantForKeyDoesNotGrantEventProcessAccess() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantSingleResourceAuthorizationForKermitGroup(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey, new UserDto(DEFAULT_USERNAME));

    // when
    final List<DefinitionWithTenantsDto> definitions = getDefinitionsAsUser(KERMIT_USER);

    // then
    assertThat(definitions).isEmpty();
  }

  @Test
  public void getDefinitions_engineUserRevokeForKeyDoesNotRevokeEventProcessAccess() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermit(definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);

    addSimpleEventProcessToElasticsearch(definitionKey, new UserDto(KERMIT_USER));

    // when
    final List<DefinitionWithTenantsDto> definitions = getDefinitionsAsUser(KERMIT_USER);

    // then
    assertThat(definitions).extracting(DefinitionWithTenantsDto::getKey).containsExactly(definitionKey);
  }

  @Test
  public void getDefinitions_engineGroupRevokeForKeyDoesNotGrantEventProcessAccess() {
    //given
    final String definitionKey = "eventProcessKey";

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      definitionKey,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );

    addSimpleEventProcessToElasticsearch(definitionKey, new UserDto(KERMIT_USER));

    // when
    final List<DefinitionWithTenantsDto> definitions = getDefinitionsAsUser(KERMIT_USER);

    // then
    assertThat(definitions).extracting(DefinitionWithTenantsDto::getKey).containsExactly(definitionKey);
  }

  @Test
  public void getProcessDefinitions_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new GroupDto(GROUP_ID));
    addSimpleEventProcessToElasticsearch(definitionKey2, new GroupDto("otherGroup"));

    // when
    final List<ProcessDefinitionOptimizeDto> definitions = getProcessDefinitionsAsUser(KERMIT_USER);

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

    addSimpleEventProcessToElasticsearch(definitionKey1, new UserDto(KERMIT_USER));
    addSimpleEventProcessToElasticsearch(definitionKey2, new UserDto(DEFAULT_USERNAME));

    // when
    final List<ProcessDefinitionOptimizeDto> definitions = getProcessDefinitionsAsUser(KERMIT_USER);

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

    addSimpleEventProcessToElasticsearch(definitionKey1, new GroupDto(GROUP_ID));
    addSimpleEventProcessToElasticsearch(definitionKey2, new GroupDto("otherGroup"));

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

    addSimpleEventProcessToElasticsearch(definitionKey1, new UserDto(KERMIT_USER));
    addSimpleEventProcessToElasticsearch(definitionKey2, new UserDto(DEFAULT_USERNAME));

    // when
    final ProcessDefinitionOptimizeDto definition = getProcessDefinitionByKeyAsUser(definitionKey1, KERMIT_USER);
    final Response definition2Response = executeGetProcessDefinitionByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definition).extracting(ProcessDefinitionOptimizeDto::getKey).isEqualTo(definitionKey1);

    assertThat(definition2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getProcessDefinitionsWithVersionsWithTenants_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new GroupDto(GROUP_ID));
    addSimpleEventProcessToElasticsearch(definitionKey2, new GroupDto("otherGroup"));

    // when
    final List<DefinitionVersionsWithTenantsDto> definitionsWithVersionsAndTenants =
      getProcessDefinitionVersionsWithTenantsAsUser(KERMIT_USER);

    // then
    assertThat(definitionsWithVersionsAndTenants)
      .extracting(DefinitionVersionsWithTenantsDto::getKey)
      .containsExactly(definitionKey1);
  }

  @Test
  public void getProcessDefinitionsWithVersionsWithTenants_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new UserDto(KERMIT_USER));
    addSimpleEventProcessToElasticsearch(definitionKey2, new UserDto(DEFAULT_USERNAME));

    // when
    final List<DefinitionVersionsWithTenantsDto> definitionsWithVersionsAndTenants =
      getProcessDefinitionVersionsWithTenantsAsUser(KERMIT_USER);

    // then
    assertThat(definitionsWithVersionsAndTenants)
      .extracting(DefinitionVersionsWithTenantsDto::getKey)
      .containsExactly(definitionKey1);
  }

  @Test
  public void getDefinitionsGroupedByTenant_groupRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new GroupDto(GROUP_ID));
    addSimpleEventProcessToElasticsearch(definitionKey2, new GroupDto("otherGroup"));

    // when
    final List<TenantWithDefinitionsDto> definitionsWithVersionsAndTenants =
      getDefinitionsGroupedByTenantAsUser(KERMIT_USER);

    // then
    assertThat(definitionsWithVersionsAndTenants)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(tenantWithDefinitionsDto -> {
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

    addSimpleEventProcessToElasticsearch(definitionKey1, new UserDto(KERMIT_USER));
    addSimpleEventProcessToElasticsearch(definitionKey2, new UserDto(DEFAULT_USERNAME));

    // when
    final List<TenantWithDefinitionsDto> definitionsWithVersionsAndTenants =
      getDefinitionsGroupedByTenantAsUser(KERMIT_USER);

    // then
    assertThat(definitionsWithVersionsAndTenants)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(tenantWithDefinitionsDto -> {
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

    addSimpleEventProcessToElasticsearch(definitionKey1, new GroupDto(GROUP_ID));
    addSimpleEventProcessToElasticsearch(definitionKey2, new GroupDto("otherGroup"));

    // when
    final String definitionXml1 = getProcessDefinitionXmlByKeyAsUser(definitionKey1, KERMIT_USER);
    final Response definitionXml2Response = executeGetProcessDefinitionXmlByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definitionXml1).isEqualTo(XML_VALUE);

    assertThat(definitionXml2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getProcessDefinitionXml_userRole() {
    // given
    final String definitionKey1 = "eventProcessKey1";
    final String definitionKey2 = "eventProcessKey2";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    addSimpleEventProcessToElasticsearch(definitionKey1, new UserDto(KERMIT_USER));
    addSimpleEventProcessToElasticsearch(definitionKey2, new UserDto(DEFAULT_USERNAME));

    // when
    final String definitionXml1 = getProcessDefinitionXmlByKeyAsUser(definitionKey1, KERMIT_USER);
    final Response definitionXml2Response = executeGetProcessDefinitionXmlByKeyAsUser(definitionKey2, KERMIT_USER);

    // then
    assertThat(definitionXml1).isEqualTo(XML_VALUE);

    assertThat(definitionXml2Response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private List<DefinitionWithTenantsDto> getDefinitionsAsUser(final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .withUserAuthentication(user, user)
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
  }

  private List<ProcessDefinitionOptimizeDto> getProcessDefinitionsAsUser(final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .withUserAuthentication(user, user)
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  private List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenantAsUser(
    final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .withUserAuthentication(user, user)
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());
  }

  private List<DefinitionVersionsWithTenantsDto> getProcessDefinitionVersionsWithTenantsAsUser(
    final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants()
      .withUserAuthentication(user, user)
      .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());
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

  private String getProcessDefinitionXmlByKeyAsUser(final String key, final String user) {
    return executeGetProcessDefinitionXmlByKeyAsUser(key, user).readEntity(String.class);
  }

  private Response executeGetProcessDefinitionXmlByKeyAsUser(final String definitionKey, final String user) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(definitionKey, EVENT_PROCESS_DEFINITION_VERSION)
      .withUserAuthentication(user, user)
      .execute();
  }

  private EventProcessDefinitionDto addSimpleEventProcessToElasticsearch(final String key,
                                                                         final IdentityDto identityDto) {
    final IndexableEventProcessMappingDto.IndexableEventProcessMappingDtoBuilder eventProcessMappingDtoBuilder =
      IndexableEventProcessMappingDto.builder()
        .id(key)
        .xml(XML_VALUE);
    if (identityDto != null) {
      eventProcessMappingDtoBuilder.roles(ImmutableList.of(new EventProcessRoleDto<>(identityDto)));
    }
    final IndexableEventProcessMappingDto eventProcessMappingDto = eventProcessMappingDtoBuilder.build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      EVENT_PROCESS_MAPPING_INDEX_NAME,
      eventProcessMappingDto.getId(),
      eventProcessMappingDto
    );
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "-1")
      .key(key)
      .name("eventProcessName")
      .version(EVENT_PROCESS_DEFINITION_VERSION)
      .bpmn20Xml(XML_VALUE)
      .flowNodeNames(Collections.emptyMap())
      .userTaskNames(Collections.emptyMap())
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      EVENT_PROCESS_DEFINITION_INDEX_NAME,
      eventProcessDefinitionDto.getId(),
      eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }
}

