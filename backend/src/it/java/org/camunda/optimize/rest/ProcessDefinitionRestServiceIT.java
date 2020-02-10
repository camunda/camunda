/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionWithTenantsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;


public class ProcessDefinitionRestServiceIT extends AbstractDefinitionRestServiceIT {

  private static final String KEY = "testKey";
  private static final String EVENT_BASED = "event based";
  private static final String NOT_EVENT_BASED = "not event based";
  private static final String ALL_VERSIONS_STRING = "ALL";
  private static final String EVENT_PROCESS_NAME = "someName";
  private static final String EXPECTED_404_MESSAGE = "Could not find xml for process definition with key";

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
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(processDefinitionOptimizeDto.getId()));
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
    final ProcessDefinitionOptimizeDto notAuthorizedToSee = addProcessDefinitionToElasticsearch(
      notAuthorizedDefinitionKey);
    final String authorizedProcessId = addProcessDefinitionToElasticsearch(authorizedDefinitionKey1).getId();
    final String authorizedEventProcessId1 = addSimpleEventProcessToElasticsearch(authorizedDefinitionKey2).getId();
    final String authorizedEventProcessId2 = addSimpleEventProcessToElasticsearch(authorizedDefinitionKey3).getId();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then we only get 3 definitions, the one kermit is authorized to see and all event based definitions
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(3));
    assertThat(definitions)
      .extracting(def -> def.getId())
      .containsExactlyInAnyOrder(authorizedProcessId, authorizedEventProcessId1, authorizedEventProcessId2);
  }

  @Test
  public void getProcessDefinitionsReturnsEventBasedWithoutAuthorization() {
    //given
    final String kermitUser = "kermit";
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey = "access";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);
    final IdDto notAuthorizedToSeeIdDto = new IdDto(addSimpleEventProcessToElasticsearch(
      notAuthorizedDefinitionKey).getId());
    final IdDto authorizedToSeeIdDto = new IdDto(addSimpleEventProcessToElasticsearch(authorizedDefinitionKey).getId());

    // when
    List<IdDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions).containsExactlyInAnyOrder(notAuthorizedToSeeIdDto, authorizedToSeeIdDto);
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
    assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
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
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(processDefinitionOptimizeDto.getId()));
    assertThat(definitions.get(0).getBpmn20Xml(), is(processDefinitionOptimizeDto.getBpmn20Xml()));
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
    assertThat(actualXml, is(expectedDto.getBpmn20Xml()));
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
    assertThat(actualXml, is(expectedDto2.getBpmn20Xml()));
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
    assertThat(actualXmlFirstTenant, is(firstTenantDefinition.getBpmn20Xml()));
    assertThat(actualXmlSecondTenant, is(secondTenantDefinition.getBpmn20Xml()));
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
    assertThat(actualXml, is(secondTenantDefinition.getBpmn20Xml()));
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
    assertThat(actualXml, is(sharedTenantDefinition.getBpmn20Xml()));
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
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
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
    assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
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
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void getEventProcessDefinitionXmlWithoutAuthorization() {
    // given
    final String kermitUser = "kermit";
    final String definitionKey = "anEventProcDefKey";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    final ProcessDefinitionOptimizeDto expectedDefinition = addSimpleEventProcessToElasticsearch(
      definitionKey
    );

    // when
    String actualXml = embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionXmlRequest(
        expectedDefinition.getKey(), expectedDefinition.getVersion()
      ).execute(String.class, Response.Status.OK.getStatusCode());

    // then the event based definition's xml is returned despite missing authorisation
    assertThat(actualXml, is(expectedDefinition.getBpmn20Xml()));
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
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(processDefinitionOptimizeDto.getKey(), "nonsenseVersion")
        .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(message.contains(EXPECTED_404_MESSAGE), is(true));
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
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest("nonsense", processDefinitionOptimizeDto.getVersion())
        .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    assertThat(message.contains(EXPECTED_404_MESSAGE), is(true));
  }

  @Test
  public void getEventProcessDefinitionVersionsWithTenants() {
    // given
    createEventProcessDefinitionsForKey(KEY, 4);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsRestDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(KEY));
    final List<TenantRestDto> expectedTenantList = ImmutableList.of(TENANT_NONE_DTO);
    assertThat(availableDefinition.getAllTenants(), is(expectedTenantList));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), is(expectedTenantList))
    );
  }

  @Test
  public void getEventAndNonEventProcessDefinitionVersionsWithTenants() {
    //given event based
    final String eventKey = "eventDefinitionKey";
    createEventProcessDefinitionsForKey(eventKey, 4);

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
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(3));

    // first definition
    final DefinitionVersionsWithTenantsRestDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(definitionKey1));
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
    assertThat(secondDefinition.getKey(), is(definitionKey2));
    final List<TenantRestDto> expectedDefinition2AllTenantsOrdered = ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO);
    assertThat(secondDefinition.getAllTenants(), is(expectedDefinition2AllTenantsOrdered));
    final List<DefinitionVersionWithTenantsRestDto> expectedVersionForDefinition2 = ImmutableList.of(
      new DefinitionVersionWithTenantsRestDto("2", VERSION_TAG, ImmutableList.of(TENANT_2_DTO)),
      new DefinitionVersionWithTenantsRestDto("1", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO)),
      new DefinitionVersionWithTenantsRestDto("0", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO))
    );
    assertThat(secondDefinition.getVersions(), is(expectedVersionForDefinition2));


    // event based definition
    final DefinitionVersionsWithTenantsRestDto eventProcessDefinition = definitions.get(2);
    assertThat(eventProcessDefinition.getKey(), is(eventKey));
    final List<TenantRestDto> expectedEventBasedTenantList = ImmutableList.of(TENANT_NONE_DTO);
    assertThat(eventProcessDefinition.getAllTenants(), is(expectedEventBasedTenantList));
    final List<DefinitionVersionWithTenantsRestDto> definitionVersions = eventProcessDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), is(expectedEventBasedTenantList))
    );
  }

  @Test
  public void getEventProcessDefinitionVersionsWithTenants_sorting() {
    addSimpleEventProcessToElasticsearch("z", "1", "a");
    addSimpleEventProcessToElasticsearch("x", "1", "b");
    createEventProcessDefinitionsForKey("c", 1);
    createEventProcessDefinitionsForKey("D", 1);
    createEventProcessDefinitionsForKey("e", 1);
    createEventProcessDefinitionsForKey("F", 1);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();

    assertThat(definitions.get(0).getKey(), is("z"));
    assertThat(definitions.get(1).getKey(), is("x"));
    assertThat(definitions.get(2).getKey(), is("c"));
    assertThat(definitions.get(3).getKey(), is("D"));
    assertThat(definitions.get(4).getKey(), is("e"));
    assertThat(definitions.get(5).getKey(), is("F"));
  }

  @Test
  public void getEventProcessDefinitionVersionsWithTenants_performance() {
    // given
    final int definitionCount = 50;
    final int versionCount = 5;

    IntStream.range(0, definitionCount)
      .mapToObj(String::valueOf)
      .parallel()
      .forEach(definitionNumber -> {
        final String definitionKey = "defKey" + definitionNumber;
        createEventProcessDefinitionsForKey(definitionKey, versionCount);
      });

    // when
    long startTimeMillis = System.currentTimeMillis();
    final List<DefinitionVersionsWithTenantsRestDto> definitions = getDefinitionVersionsWithTenants();
    long responseTimeMillis = System.currentTimeMillis() - startTimeMillis;

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(definitionCount));
    definitions.forEach(definitionVersionsWithTenantsRestDto -> {
      assertThat(definitionVersionsWithTenantsRestDto.getVersions().size(), is(versionCount));
      assertThat(definitionVersionsWithTenantsRestDto.getAllTenants().size(), is(1)); // only null tenant
    });
    assertThat(responseTimeMillis, is(lessThan(2000L)));

    embeddedOptimizeExtension.getImportSchedulerFactory().shutdown();
  }

  @Override
  protected List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenantsAsUser(final String userId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(userId, userId)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, Response.Status.OK.getStatusCode());
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

  private ProcessDefinitionOptimizeDto addDefinitionToElasticsearch(final String key,
                                                                    final String type) {
    return addDefinitionToElasticsearch(key, "1", type);
  }

  private ProcessDefinitionOptimizeDto addDefinitionToElasticsearch(final String key,
                                                                    final String version,
                                                                    final String type) {
    switch (type) {
      case EVENT_BASED:
        return addSimpleEventProcessToElasticsearch(key, version);
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

  protected EventProcessDefinitionDto addSimpleEventProcessToElasticsearch(final String key) {
    return addSimpleEventProcessToElasticsearch(key, "1", EVENT_PROCESS_NAME);
  }

  protected EventProcessDefinitionDto addSimpleEventProcessToElasticsearch(final String key,
                                                                           final String version) {
    return addSimpleEventProcessToElasticsearch(key, version, EVENT_PROCESS_NAME);
  }

  protected EventProcessDefinitionDto addSimpleEventProcessToElasticsearch(final String key,
                                                                           final String version,
                                                                           final String name) {
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "-" + version)
      .key(key)
      .name(name)
      .version(version)
      .bpmn20Xml(key + version)
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

  private void createProcessDefinitionsForKey(String key, int count, String tenantId) {
    IntStream.range(0, count).forEach(
      i -> addProcessDefinitionToElasticsearch(key, String.valueOf(i), tenantId)
    );
  }

  private void createEventProcessDefinitionsForKey(String key, int count) {
    IntStream.range(0, count).forEach(
      i -> addSimpleEventProcessToElasticsearch(key, String.valueOf(i), EVENT_PROCESS_NAME)
    );
  }
}
