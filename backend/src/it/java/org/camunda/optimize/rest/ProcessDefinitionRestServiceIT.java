/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
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

  @ParameterizedTest(name = "Get {0} process definitions.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitions(final String processDefinitionType) {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addDefinitionToElasticsearch(
      KEY,
      processDefinitionType
    );

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then the status code is okay
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionOptimizeDto.getId());
  }

  @Test
  public void getProcessDefinitionsReturnOnlyThoseAuthorizedToSeeAndAllEventProcessDefinitions() {
    //given
    final String kermitUser = "kermit";
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey1 = "access1";
    final String authorizedDefinitionKey2 = "access2";
    final String authorizedDefinitionKey3 = "access3";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey1);
    final ProcessDefinitionOptimizeDto notAuthorizedToSee =
      addProcessDefinitionToElasticsearch(notAuthorizedDefinitionKey);
    final String authorizedProcessId = addProcessDefinitionToElasticsearch(authorizedDefinitionKey1).getId();
    final String authorizedEventProcessId1 = elasticSearchIntegrationTestExtension
      .addEventProcessDefinitionDtoToElasticsearch(authorizedDefinitionKey2, new UserDto(kermitUser)).getId();
    final String authorizedEventProcessId2 = elasticSearchIntegrationTestExtension
      .addEventProcessDefinitionDtoToElasticsearch(authorizedDefinitionKey3, new UserDto(kermitUser)).getId();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then we only get 3 definitions, the one kermit is authorized to see and all event based definitions
    assertThat(definitions)
      .extracting(DefinitionOptimizeDto::getId)
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
    //given
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
    //given
    ProcessDefinitionOptimizeDto expectedDto = addDefinitionToElasticsearch(KEY, processDefinitionType);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(expectedDto.getKey(), expectedDto.getVersion())
        .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXml).isEqualTo(expectedDto.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get the latest XML of {0} process definition.")
  @MethodSource("processDefinitionTypes")
  public void getLatestProcessDefinitionXml(final String processDefinitionType) {
    //given
    ProcessDefinitionOptimizeDto expectedDto1 = addDefinitionToElasticsearch(KEY, "1", processDefinitionType);
    ProcessDefinitionOptimizeDto expectedDto2 = addDefinitionToElasticsearch(KEY, "2", processDefinitionType);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(KEY, ALL_VERSIONS_STRING)
        .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXml).isEqualTo(expectedDto2.getBpmn20Xml());
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
      .execute(String.class, Response.Status.OK.getStatusCode());
    final String actualXmlSecondTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(KEY, "1", secondTenantId)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXmlFirstTenant).isEqualTo(firstTenantDefinition.getBpmn20Xml());
    assertThat(actualXmlSecondTenant).isEqualTo(secondTenantDefinition.getBpmn20Xml());
  }

  @Test
  public void getSharedProcessDefinitionXmlByNullTenant() {
    //given
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
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(
          secondTenantDefinition.getKey(), secondTenantDefinition.getVersion(), null
        )
        .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXml).isEqualTo(secondTenantDefinition.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get XML of {0} process definition for null tenant.")
  @MethodSource("processDefinitionTypes")
  public void getSharedProcessDefinitionXmlByTenantWithNoSpecificDefinition(final String processDefinitionType) {
    //given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto sharedTenantDefinition = addDefinitionToElasticsearch(KEY, processDefinitionType);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(
          sharedTenantDefinition.getKey(), sharedTenantDefinition.getVersion(), firstTenantId
        )
        .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXml).isEqualTo(sharedTenantDefinition.getBpmn20Xml());
  }

  @ParameterizedTest(name = "Get XML of {0} process definition with null parameter returns 404.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionXmlWithNullParameter(final String processDefinitionType) {
    //given
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getEventProcessDefinitionXmlWithoutAuthorization() {
    // given
    final String kermitUser = "kermit";
    final String definitionKey = "anEventProcDefKey";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    final ProcessDefinitionOptimizeDto expectedDefinition = elasticSearchIntegrationTestExtension
      .addEventProcessDefinitionDtoToElasticsearch(definitionKey);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionXmlRequest(
        expectedDefinition.getKey(), expectedDefinition.getVersion()
      ).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "Get {0} process definition with nonexistent version returns 404 message.")
  @MethodSource("processDefinitionTypes")
  public void getProcessDefinitionXmlWithNonsenseVersionReturns404Code(final String processDefinitionType) {
    //given
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
    //given
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

  @Test
  public void getEventProcessDefinitionVersionsWithTenants() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(KEY);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions).isNotNull().hasSize(1);
    final DefinitionVersionsWithTenantsDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey()).isEqualTo(KEY);
    final List<TenantDto> expectedTenantList = ImmutableList.of(TENANT_NONE_DTO);
    assertThat(availableDefinition.getAllTenants()).isEqualTo(expectedTenantList);
    final List<DefinitionVersionWithTenantsDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants()).isEqualTo(expectedTenantList)
    );
  }

  @Test
  public void getEventAndNonEventProcessDefinitionVersionsWithTenants() {
    //given event based
    final String eventKey = "eventDefinitionKey";
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(eventKey);

    // given non event based
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey1 = "definitionKey1";
    final String definitionKey2 = "definitionKey2";
    createDefinitionsForKey(definitionKey1, 3);
    createDefinitionsForKey(definitionKey2, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey2, 3, TENANT_2_DTO.getId());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions).isNotNull().hasSize(3);

    // first definition
    final DefinitionVersionsWithTenantsDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey()).isEqualTo(definitionKey1);
    final List<TenantDto> expectedDefinition1AllTenantsOrdered = ImmutableList.of(
      TENANT_NONE_DTO, TENANT_1_DTO, TENANT_2_DTO
    );
    assertThat(firstDefinition.getAllTenants()).isEqualTo(expectedDefinition1AllTenantsOrdered);
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, expectedDefinition1AllTenantsOrdered)
    );
    assertThat(firstDefinition.getVersions()).isEqualTo(expectedVersionForDefinition1);

    // second definition
    final DefinitionVersionsWithTenantsDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey()).isEqualTo(definitionKey2);
    final List<TenantDto> expectedDefinition2AllTenantsOrdered = ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO);
    assertThat(secondDefinition.getAllTenants()).isEqualTo(expectedDefinition2AllTenantsOrdered);
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition2 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, ImmutableList.of(TENANT_2_DTO)),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO)),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO))
    );
    assertThat(secondDefinition.getVersions()).isEqualTo(expectedVersionForDefinition2);


    // event based definition
    final DefinitionVersionsWithTenantsDto eventProcessDefinition = definitions.get(2);
    assertThat(eventProcessDefinition.getKey()).isEqualTo(eventKey);
    final List<TenantDto> expectedEventBasedTenantList = ImmutableList.of(TENANT_NONE_DTO);
    assertThat(eventProcessDefinition.getAllTenants()).isEqualTo(expectedEventBasedTenantList);
    final List<DefinitionVersionWithTenantsDto> definitionVersions = eventProcessDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants()).isEqualTo(expectedEventBasedTenantList)
    );
  }

  @Test
  public void getProcessDefinitionVersionsWithTenants_excludeEventProcesses() {
    //given event based
    final String eventKey = "eventDefinitionKey";
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(eventKey);

    // given non event based
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey1 = "definitionKey1";
    final String definitionKey2 = "definitionKey2";
    createDefinitionsForKey(definitionKey1, 3);
    createDefinitionsForKey(definitionKey2, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey2, 3, TENANT_2_DTO.getId());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants(true);

    // then
    assertThat(definitions).isNotNull().hasSize(2);

    // first definition
    final DefinitionVersionsWithTenantsDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey()).isEqualTo(definitionKey1);
    final List<TenantDto> expectedDefinition1AllTenantsOrdered = ImmutableList.of(
      TENANT_NONE_DTO, TENANT_1_DTO, TENANT_2_DTO
    );
    assertThat(firstDefinition.getAllTenants()).isEqualTo(expectedDefinition1AllTenantsOrdered);
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, expectedDefinition1AllTenantsOrdered)
    );
    assertThat(firstDefinition.getVersions()).isEqualTo(expectedVersionForDefinition1);

    // second definition
    final DefinitionVersionsWithTenantsDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey()).isEqualTo(definitionKey2);
    final List<TenantDto> expectedDefinition2AllTenantsOrdered = ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO);
    assertThat(secondDefinition.getAllTenants()).isEqualTo(expectedDefinition2AllTenantsOrdered);
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition2 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, ImmutableList.of(TENANT_2_DTO)),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO)),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO))
    );
    assertThat(secondDefinition.getVersions()).isEqualTo(expectedVersionForDefinition2);
  }

  protected List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenants(final boolean excludeEventProcesses) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionVersionsWithTenants(null, excludeEventProcesses)
      .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());
  }

  @Test
  public void getEventProcessDefinitionVersionsWithTenants_sorting() {
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("z", "a");
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("x", "b");
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("c");
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("D");
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("e");
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch("F");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();
    assertThat(definitions)
      .extracting(DefinitionVersionsWithTenantsDto::getKey)
      .containsExactly("z", "x", "c", "D", "e", "F");
  }

  @Test
  public void getEventProcessDefinitionVersionsWithTenants_performance() {
    // given
    final int definitionCount = 50;

    IntStream.range(0, definitionCount)
      .mapToObj(String::valueOf)
      .parallel()
      .forEach(definitionNumber -> {
        final String definitionKey = "defKey" + definitionNumber;
        elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(definitionKey);
      });

    // when
    long startTimeMillis = System.currentTimeMillis();
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();
    long responseTimeMillis = System.currentTimeMillis() - startTimeMillis;

    // then
    assertThat(definitions).isNotNull().hasSize(definitionCount);
    definitions.forEach(DefinitionVersionsWithTenantsDto -> {
      assertThat(DefinitionVersionsWithTenantsDto.getVersions()).hasSize(1);
      assertThat(DefinitionVersionsWithTenantsDto.getAllTenants()).hasSize(1); // only null tenant
    });
    assertThat(responseTimeMillis).isLessThan(6000L);

    embeddedOptimizeExtension.getImportSchedulerFactory().shutdown();
  }

  @Override
  protected List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenantsAsUser(String userId,
                                                                                          String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(userId, userId)
      .buildGetProcessDefinitionVersionsWithTenants(collectionId)
      .executeAndReturnList(DefinitionVersionsWithTenantsDto.class, Response.Status.OK.getStatusCode());
  }

  @Override
  protected void createDefinitionsForKey(final String definitionKey, final int versionCount, final String tenantId) {
    createProcessDefinitionsForKey(definitionKey, versionCount, tenantId);
  }

  @Override
  protected void createDefinition(final String key, final String version, final String tenantId, final String name) {
    addProcessDefinitionToElasticsearch(key, version, tenantId, name);
  }

  @Override
  protected int getDefinitionResourceType() {
    return RESOURCE_TYPE_PROCESS_DEFINITION;
  }

  @Override
  protected DefinitionType getDefinitionType() {
    return DefinitionType.PROCESS;
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
    return addProcessDefinitionToElasticsearch(key, "1", tenantId, null);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId) {
    return addProcessDefinitionToElasticsearch(key, version, tenantId, null);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String tenantId,
                                                                           final String name) {
    final ProcessDefinitionOptimizeDto expectedDto = ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId)
      .key(key)
      .name(name)
      .version(version)
      .versionTag(VERSION_TAG)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(key + version + tenantId)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedDto.getId(),
      expectedDto
    );
    return expectedDto;
  }

  private void createProcessDefinitionsForKey(String key, int count, String tenantId) {
    IntStream.range(0, count).forEach(
      i -> addProcessDefinitionToElasticsearch(key, String.valueOf(i), tenantId)
    );
  }

}
