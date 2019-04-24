/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessDefinitionRestServiceIT {
  private static final String DIAGRAM = "org/camunda/optimize/service/es/reader/gateway_process.bpmn";
  private static final String PROCESS_DEFINITION_ID_2 = "procDef2";
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String PROCESS_DEFINITION_KEY = "procDef";
  private static final String PROCESS_DEFINITION_VERSION_1 = "1";
  private static final String PROCESS_DEFINITION_VERSION_2 = "2";
  private static final String END_ACTIVITY = "endActivity";
  private static final String GATEWAY_ACTIVITY = "gw_1";
  private static final String PROCESS_INSTANCE_ID = "processInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  private static final String TASK = "task_1";

  private static final String KEY = "testKey";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;

  @Before
  public void init() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitions() {
    //given
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
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
    engineRule.addUser(kermitUser, kermitUser);
    engineRule.grantUserOptimizeAccess(kermitUser);
    grantSingleDefinitionAuthorizationsForUser(kermitUser, authorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto notAuthorizedToSee = addProcessDefinitionToElasticsearch(
      notAuthorizedDefinitionKey);
    final ProcessDefinitionOptimizeDto authorizedToSee = addProcessDefinitionToElasticsearch(authorizedDefinitionKey);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
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
    Response response = embeddedOptimizeRule
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
      embeddedOptimizeRule
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
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(expectedDto.getKey(), expectedDto.getVersion())
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(expectedDto.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXmlWithNullParameter() {
    //given
    ProcessDefinitionOptimizeDto expectedDto = addProcessDefinitionToElasticsearch(KEY);

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(null, expectedDto.getVersion())
        .execute(String.class, 404);
  }

  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule
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
    engineRule.addUser(kermitUser, kermitUser);
    engineRule.grantUserOptimizeAccess(kermitUser);
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = addProcessDefinitionToElasticsearch(
      definitionKey
    );

    // when
    Response response = embeddedOptimizeRule.getRequestExecutor()
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
      embeddedOptimizeRule
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
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest("nonesense", processDefinitionOptimizeDto.getVersion())
        .execute(String.class, 404);

    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getCorrelationWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildProcessDefinitionCorrelation(new BranchAnalysisQueryDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCorrelation() throws IOException {
    //given
    setupFullInstanceFlow();

    // when
    BranchAnalysisQueryDto branchAnalysisQueryDto = new BranchAnalysisQueryDto();
    branchAnalysisQueryDto.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    branchAnalysisQueryDto.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1);
    branchAnalysisQueryDto.setGateway(GATEWAY_ACTIVITY);
    branchAnalysisQueryDto.setEnd(END_ACTIVITY);

    Response response =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildProcessDefinitionCorrelation(branchAnalysisQueryDto)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    BranchAnalysisDto actual =
      response.readEntity(BranchAnalysisDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getTotal(), is(2L));
  }

  @Test
  public void testGetProcessDefinitionsGroupedByKey() {
    //given
    createProcessDefinitionsForKey("procDefKey1", 11);
    createProcessDefinitionsForKey("procDefKey2", 2);

    // when
    List<ProcessDefinitionGroupOptimizeDto> actual = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionsGroupedByKeyRequest()
      .executeAndReturnList(ProcessDefinitionGroupOptimizeDto.class, 200);

    // then
    assertThat(actual, is(notNullValue()));
    assertThat(actual.size(), is(2));
    // assert that procDefKey1 comes first in list
    actual.sort(Comparator.comparing(
      ProcessDefinitionGroupOptimizeDto::getVersions,
      Comparator.comparing(v -> v.get(0).getKey())
    ));
    ProcessDefinitionGroupOptimizeDto procDefs1 = actual.get(0);
    assertThat(procDefs1.getKey(), is("procDefKey1"));
    assertThat(procDefs1.getVersions().size(), is(11));
    assertThat(procDefs1.getVersions().get(0).getVersion(), is("10"));
    assertThat(procDefs1.getVersions().get(1).getVersion(), is("9"));
    assertThat(procDefs1.getVersions().get(2).getVersion(), is("8"));
    ProcessDefinitionGroupOptimizeDto procDefs2 = actual.get(1);
    assertThat(procDefs2.getKey(), is("procDefKey2"));
    assertThat(procDefs2.getVersions().size(), is(2));
    assertThat(procDefs2.getVersions().get(0).getVersion(), is("1"));
    assertThat(procDefs2.getVersions().get(1).getVersion(), is("0"));
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key) {
    return addProcessDefinitionToElasticsearch(key, "1");
  }

  private ProcessDefinitionOptimizeDto addProcessDefinitionToElasticsearch(final String key, final String version) {
    final ProcessDefinitionOptimizeDto expectedDto = new ProcessDefinitionOptimizeDto()
      .setId(key + version)
      .setKey(key)
      .setVersion(version)
      .setBpmn20Xml("ProcessModelXml");
    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, expectedDto.getId(), expectedDto);
    return expectedDto;
  }

  private void createProcessDefinitionsForKey(String key, int count) {
    IntStream.range(0, count).forEach(
      i -> addProcessDefinitionToElasticsearch(key, String.valueOf(i))
    );
  }

  private void setupFullInstanceFlow() throws IOException {
    final ProcessDefinitionOptimizeDto processDefinitionXmlDto = new ProcessDefinitionOptimizeDto()
      .setId(PROCESS_DEFINITION_ID)
      .setKey(PROCESS_DEFINITION_KEY)
      .setVersion(PROCESS_DEFINITION_VERSION_1)
      .setBpmn20Xml(readDiagram(DIAGRAM));
    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, PROCESS_DEFINITION_ID, processDefinitionXmlDto);

    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    processDefinitionXmlDto.setVersion(PROCESS_DEFINITION_VERSION_2);
    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);

    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessDefinitionId(PROCESS_DEFINITION_ID)
      .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1)
      .setProcessInstanceId(PROCESS_INSTANCE_ID)
      .setStartDate(OffsetDateTime.now())
      .setEndDate(OffsetDateTime.now())
      .setEvents(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK}));
    elasticSearchRule.addEntryToElasticsearch(PROC_INSTANCE_TYPE, PROCESS_INSTANCE_ID, procInst);

    procInst.setEvents(
      createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY})
    );
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    elasticSearchRule.addEntryToElasticsearch(PROC_INSTANCE_TYPE, PROCESS_INSTANCE_ID_2, procInst);
  }

  private List<SimpleEventDto> createEventList(String[] activityIds) {
    List<SimpleEventDto> events = new ArrayList<>(activityIds.length);
    for (String activityId : activityIds) {
      SimpleEventDto event = new SimpleEventDto();
      event.setActivityId(activityId);
      events.add(event);
    }
    return events;
  }

  private String readDiagram(String diagramPath) throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath));
  }

  private static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }
}
