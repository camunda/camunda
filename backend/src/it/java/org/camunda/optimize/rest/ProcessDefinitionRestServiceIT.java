/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class ProcessDefinitionRestServiceIT extends AbstractDefinitionRestServiceIT {

  private static final String KEY = "testKey";
  private static final String EVENT_BASED = "event based";
  private static final String NOT_EVENT_BASED = "not event based";
  private static final String ALL_VERSIONS_STRING = "ALL";

  private static Stream<String> processDefinitionTypes() {
    return Stream.of(EVENT_BASED, NOT_EVENT_BASED);
  }

  @ParameterizedTest
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitions(final String processDefinitionType) {
    // given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addDefinitionToElasticsearch(
      KEY,
      processDefinitionType
    );

    // when
    List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitions();

    // then
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionOptimizeDto.getId());
  }

  @Test
  public void getProcessDefinitionsExcludesDeletedDefinitions() {
    // given
    final ProcessDefinitionOptimizeDto definition = addProcessDefinitionToElasticsearch(KEY, "1", null, false);
    // the deleted definition will be excluded from the results
    addProcessDefinitionToElasticsearch(KEY, "2", null, true);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitions();

    // then
    assertThat(definitions).singleElement()
      .satisfies(returnedDef -> {
        assertThat(returnedDef.getKey()).isEqualTo(definition.getKey());
        assertThat(returnedDef.isDeleted()).isFalse();
      });
  }

  @Test
  public void getProcessDefinitionsReturnOnlyThoseAuthorizedToSeeAndAllEventProcessDefinitions() {
    // given
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey1 = "access1";
    final String authorizedDefinitionKey2 = "access2";
    final String authorizedDefinitionKey3 = "access3";
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, authorizedDefinitionKey1);
    addProcessDefinitionToElasticsearch(notAuthorizedDefinitionKey);
    final String authorizedProcessId = addProcessDefinitionToElasticsearch(authorizedDefinitionKey1).getId();
    final String authorizedEventProcessId1 = elasticSearchIntegrationTestExtension
      .addEventProcessDefinitionDtoToElasticsearch(authorizedDefinitionKey2, new UserDto(KERMIT_USER)).getId();
    final String authorizedEventProcessId2 = elasticSearchIntegrationTestExtension
      .addEventProcessDefinitionDtoToElasticsearch(authorizedDefinitionKey3, new UserDto(KERMIT_USER)).getId();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitionsAsUser(
      KERMIT_USER,
      KERMIT_USER
    );

    // then we only get 3 definitions, the one kermit is authorized to see and all event based definitions
    assertThat(definitions)
      .extracting(DefinitionOptimizeResponseDto::getId)
      .containsExactlyInAnyOrder(authorizedProcessId, authorizedEventProcessId1, authorizedEventProcessId2);
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest(name = "Get {0} process definitions with XML.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionsWithXml(final String processDefinitionType) {
    // given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addDefinitionToElasticsearch(
      KEY,
      processDefinitionType
    );

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionOptimizeDto.getId());
    assertThat(definitions.get(0).getBpmn20Xml()).isEqualTo(processDefinitionOptimizeDto.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get XML of {0} process definition.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionXml(final String processDefinitionType) {
    // given
    ProcessDefinitionOptimizeDto expectedDto = addDefinitionToElasticsearch(KEY, processDefinitionType);

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(expectedDto.getKey(), expectedDto.getVersion(), null);

    // then
    assertThat(actualXml).isEqualTo(expectedDto.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get the latest XML of {0} process definition for ALL version selection.")
  @MethodSource("processDefinitionTypes")
  public void getAllProcessDefinitionXml(final String processDefinitionType) {
    // given
    ProcessDefinitionOptimizeDto expectedDto1 = addDefinitionToElasticsearch(KEY, "1", processDefinitionType);
    ProcessDefinitionOptimizeDto expectedDto2 = addDefinitionToElasticsearch(KEY, "2", processDefinitionType);

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(KEY, ALL_VERSIONS_STRING, null);

    // then
    assertThat(actualXml).isEqualTo(expectedDto2.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get the latest XML of {0} process definition for LATEST version selection.")
  @MethodSource("processDefinitionTypes")
  public void getLatestProcessDefinitionXml(final String processDefinitionType) {
    // given
    addDefinitionToElasticsearch(KEY, "1", processDefinitionType);
    ProcessDefinitionOptimizeDto expectedDto = addDefinitionToElasticsearch(KEY, "2", processDefinitionType);

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(KEY, LATEST_VERSION, null);

    // then
    assertThat(actualXml).isEqualTo(expectedDto.getBpmn20Xml());
  }

  @Test
  public void getProcessDefinitionXmlByTenant() {
    // given
    final String firstTenantId = "tenant1";
    final String secondTenantId = "tenant2";
    ProcessDefinitionOptimizeDto firstTenantDefinition = addProcessDefinitionToElasticsearch(KEY, firstTenantId);
    ProcessDefinitionOptimizeDto secondTenantDefinition = addProcessDefinitionToElasticsearch(KEY, secondTenantId);

    // when
    final String actualXmlFirstTenant = definitionClient.getProcessDefinitionXml(KEY, "1", firstTenantId);
    final String actualXmlSecondTenant = definitionClient.getProcessDefinitionXml(KEY, "1", secondTenantId);
    // then
    assertThat(actualXmlFirstTenant).isEqualTo(firstTenantDefinition.getBpmn20Xml());
    assertThat(actualXmlSecondTenant).isEqualTo(secondTenantDefinition.getBpmn20Xml());
  }

  @Test
  public void getSharedProcessDefinitionXmlByNullTenant() {
    // given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto firstTenantDefinition = addProcessDefinitionToElasticsearch(
      KEY,
      firstTenantId
    );
    ProcessDefinitionOptimizeDto secondTenantDefinition = addProcessDefinitionToElasticsearch(
      KEY,
      null
    );

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(
      secondTenantDefinition.getKey(),
      secondTenantDefinition.getVersion(),
      null
    );

    // then
    assertThat(actualXml).isEqualTo(secondTenantDefinition.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get XML of {0} process definition for null tenant.")
  @MethodSource("processDefinitionTypes")
  public void getSharedProcessDefinitionXmlByTenantWithNoSpecificDefinition(final String processDefinitionType) {
    // given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto sharedTenantDefinition = addDefinitionToElasticsearch(KEY, processDefinitionType);

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(
      sharedTenantDefinition.getKey(),
      sharedTenantDefinition.getVersion(),
      firstTenantId
    );

    // then
    assertThat(actualXml).isEqualTo(sharedTenantDefinition.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get XML of {0} process definition with null parameter returns 404.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionXmlWithNullParameter(final String processDefinitionType) {
    // given
    ProcessDefinitionOptimizeDto expectedDto = addDefinitionToElasticsearch(KEY, processDefinitionType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(null, expectedDto.getVersion())
      .execute();

    // then the status code is not found
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getProcessDefinitionXmlWithoutAuthorization() {
    // given
    final String definitionKey = "aProcDefKey";
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(
      definitionKey
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetProcessDefinitionXmlRequest(
        processDefinitionOptimizeDto.getKey(), processDefinitionOptimizeDto.getVersion()
      ).execute();

    // then the status code is forbidden
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getEventProcessDefinitionXmlWithoutAuthorization() {
    // given
    final String definitionKey = "anEventProcDefKey";
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    final ProcessDefinitionOptimizeDto expectedDefinition = elasticSearchIntegrationTestExtension
      .addEventProcessDefinitionDtoToElasticsearch(definitionKey);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetProcessDefinitionXmlRequest(
        expectedDefinition.getKey(), expectedDefinition.getVersion()
      ).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "Get {0} process definition with nonexistent version returns 404 message.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionXmlWithNonsenseVersionReturns404Code(final String processDefinitionType) {
    // given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto =
      addDefinitionToElasticsearch(
        KEY,
        processDefinitionType
      );

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(processDefinitionOptimizeDto.getKey(), "nonsenseVersion")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response.readEntity(String.class)).contains(EXPECTED_DEFINITION_NOT_FOUND_MESSAGE);
  }

  @ParameterizedTest(name = "Get {0} process definition with nonexistent key returns 404 message.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionXmlWithNonsenseKeyReturns404Code(final String processDefinitionType) {
    // given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto =
      addDefinitionToElasticsearch(
        KEY,
        processDefinitionType
      );

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest("nonsense", processDefinitionOptimizeDto.getVersion())
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(response.readEntity(String.class)).contains(EXPECTED_DEFINITION_NOT_FOUND_MESSAGE);
  }

  @Override
  protected int getDefinitionResourceType() {
    return RESOURCE_TYPE_PROCESS_DEFINITION;
  }

  private ProcessDefinitionOptimizeDto addDefinitionToElasticsearch(final String key,
                                                                    final String type) {
    return addDefinitionToElasticsearch(key, "1", type);
  }

  private ProcessDefinitionOptimizeDto addDefinitionToElasticsearch(final String key,
                                                                    final String version,
                                                                    final String type) {
    switch (type) {
      case EVENT_BASED:
        return elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
          key, key, version, Collections.singletonList(new IdentityDto(DEFAULT_USERNAME, IdentityType.USER))
        );
      case NOT_EVENT_BASED:
        return addProcessDefinitionToElasticsearch(key, version, null);
      default:
        throw new OptimizeIntegrationTestException("Unsupported Process Definition Type: " + type);
    }
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key) {
    return addProcessDefinitionToElasticsearch(key, null);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String tenantId) {
    return addProcessDefinitionToElasticsearch(key, "1", tenantId);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId) {
    return addProcessDefinitionToElasticsearch(key, version, tenantId, false);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId,
                                                                           final boolean deleted) {
    final ProcessDefinitionOptimizeDto expectedDto = ProcessDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(key)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml(key + version + tenantId)
      .deleted(deleted)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedDto.getId(),
      expectedDto
    );
    return expectedDto;
  }

}
