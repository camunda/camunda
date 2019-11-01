/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessDefinitionRestServiceIT extends AbstractDefinitionRestServiceIT {

  private static final String VERSION_TAG = "aVersionTag";
  private static final String KEY = "testKey";

  @Test
  public void getProcessDefinitions() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then the status code is okay
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(processDefinitionOptimizeDto.getId()));
  }

  @Test
  public void getProcessDefinitionsReturnOnlyThoseAuthorizedToSee() {
    //given
    final String kermitUser = "kermit";
    final String notAuthorizedDefinitionKey = "noAccess";
    final String authorizedDefinitionKey = "access";
    engineIntegrationExtension.addUser(kermitUser, kermitUser);
    engineIntegrationExtension.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto notAuthorizedToSee = addProcessDefinitionToElasticsearch(
      notAuthorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto authorizedToSee = addProcessDefinitionToElasticsearch(authorizedDefinitionKey);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(kermitUser, kermitUser)
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then we only get 1 definition, the one kermit is authorized to see
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(authorizedToSee.getId()));
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
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionsWithXml() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(processDefinitionOptimizeDto.getId()));
    assertThat(definitions.get(0).getBpmn20Xml(), is(processDefinitionOptimizeDto.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXml() {
    //given
    ProcessDefinitionOptimizeDto expectedDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(expectedDto.getKey(), expectedDto.getVersion())
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(expectedDto.getBpmn20Xml()));
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
      .execute(String.class, 200);
    final String actualXmlSecondTenant = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(KEY, "1", secondTenantId)
      .execute(String.class, 200);

    // then
    assertThat(actualXmlFirstTenant, is(firstTenantDefinition.getBpmn20Xml()));
    assertThat(actualXmlSecondTenant, is(secondTenantDefinition.getBpmn20Xml()));
  }

  @Test
  public void getSharedProcessDefinitionXmlByNullTenant() {
    //given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto firstTenantDefinition = addProcessDefinitionToElasticsearch(KEY, firstTenantId);
    ProcessDefinitionOptimizeDto secondTenantDefinition = addProcessDefinitionToElasticsearch(KEY, null);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(
          secondTenantDefinition.getKey(), secondTenantDefinition.getVersion(), null
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(secondTenantDefinition.getBpmn20Xml()));
  }

  @Test
  public void getSharedProcessDefinitionXmlByTenantWithNoSpecificDefinition() {
    //given
    final String firstTenantId = "tenant1";
    ProcessDefinitionOptimizeDto sharedTenantDefinition = addProcessDefinitionToElasticsearch(KEY, null);

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(
          sharedTenantDefinition.getKey(), sharedTenantDefinition.getVersion(), firstTenantId
        )
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(sharedTenantDefinition.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXmlWithNullParameter() {
    //given
    ProcessDefinitionOptimizeDto expectedDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(null, expectedDto.getVersion())
      .execute(String.class, 404);
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
    assertThat(response.getStatus(), is(401));
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
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseVersionReturns404Code() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(processDefinitionOptimizeDto.getKey(), "nonsenseVersion")
        .execute(String.class, 404);

    // then
    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseKeyReturns404Code() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String message =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest("nonesense", processDefinitionOptimizeDto.getVersion())
        .execute(String.class, 404);

    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Override
  protected List<DefinitionVersionsWithTenantsRestDto> getDefinitionVersionsWithTenantsAsUser(final String userId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(userId, userId)
      .buildGetProcessDefinitionVersionsWithTenants()
      .executeAndReturnList(DefinitionVersionsWithTenantsRestDto.class, 200);
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

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key) {
    return addProcessDefinitionToElasticsearch(key, null);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key, final String tenantId) {
    return addProcessDefinitionToElasticsearch(key, "1", tenantId, key);
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
    final ProcessDefinitionOptimizeDto expectedDto = new ProcessDefinitionOptimizeDto()
      .setId(key + "-" + version + "-" + tenantId)
      .setKey(key)
      .setName(name)
      .setVersion(version)
      .setVersionTag(VERSION_TAG)
      .setTenantId(tenantId)
      .setEngine(DEFAULT_ENGINE_ALIAS)
      .setBpmn20Xml(key + version + tenantId);
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
