/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_OF_LAST_ENTITY;
import static org.camunda.optimize.service.security.EngineAuthenticationProvider.CONNECTION_WAS_REFUSED_ERROR;
import static org.camunda.optimize.service.security.EngineAuthenticationProvider.INVALID_CREDENTIALS_ERROR_MESSAGE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;


public class MultipleEngineSupportIT {

  private static final String REST_ENDPOINT = "http://localhost:8080/engine-rest";
  private static final String SECURE_REST_ENDPOINT = "http://localhost:8080/engine-rest-secure";

  private final String SECOND_ENGINE_ALIAS = "secondTestEngine";
  private EngineIntegrationRule defaultEngineRule = new EngineIntegrationRule();
  private EngineIntegrationRule secondEngineRule =
    new EngineIntegrationRule("multiple-engine/multiple-engine-integration-rules.properties");
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(defaultEngineRule).around(embeddedOptimizeRule);

  @Before
  public void init() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  @After
  public void reset() {
    configurationService.getConfiguredEngines().remove(SECOND_ENGINE_ALIAS);
    embeddedOptimizeRule.reloadConfiguration();
  }

  @Test
  public void allProcessDefinitionXmlsAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(ElasticsearchConstants.PROC_DEF_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
    }
  }

  private SearchResponse performProcessDefinitionSearchRequest(String procDefType) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(procDefType))
      .types(procDefType)
      .source(searchSourceBuilder);

    try {
      return elasticSearchRule.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the import count!", e);
    }
  }

  @Test
  public void allProcessDefinitionsAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(ElasticsearchConstants.PROC_DEF_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
    }
  }

  @Test
  public void allProcessInstancesEventAndVariablesAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(ElasticsearchConstants.PROC_INSTANCE_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertImportResults(searchResponse, allowedProcessDefinitionKeys);
  }

  private void assertImportResults(SearchResponse searchResponse, Set<String> allowedProcessDefinitionKeys) {
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(ProcessInstanceType.PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
      List events = (List) searchHit.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(2));
      List stringVariables = (List) searchHit.getSourceAsMap().get(STRING_VARIABLES);
      //NOTE: independent from process definition
      assertThat(stringVariables.size(), is(1));
    }
  }

  @Test
  public void allProcessInstancesEventAndVariablesAreImportedWithAuthentication() {
    // given
    secondEngineRule.addUser("admin", "admin");
    secondEngineRule.grantAllAuthorizations("admin");
    addSecureEngineToConfiguration();
    embeddedOptimizeRule.reloadConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(ElasticsearchConstants.PROC_INSTANCE_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertImportResults(searchResponse, allowedProcessDefinitionKeys);
  }

  @Test
  public void userIsAuthenticatedAgainstEachEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultEngineRule.addUser("kermit", "kermit");
    defaultEngineRule.grantUserOptimizeAccess("kermit");
    secondEngineRule.addUser("gonzo", "gonzo");
    secondEngineRule.grantUserOptimizeAccess("gonzo");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    // then
    assertThat(response.getStatus(), is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));

    response = embeddedOptimizeRule.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus(), is(200));
    responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));
  }

  @Test
  public void userIsAuthenticatedAgainstEachEngineEvenIfOneEngineIsDown() {
    // given
    addNonExistingSecondEngineToConfiguration();
    defaultEngineRule.addUser("kermit", "kermit");
    defaultEngineRule.grantUserOptimizeAccess("kermit");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    // then
    assertThat(response.getStatus(), is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));
  }

  @Test
  public void userCantBeAuthenticatedAgainstAnyEngine() {
    // given
    addNonExistingSecondEngineToConfiguration();
    defaultEngineRule.addUser("kermit", "kermit");
    defaultEngineRule.grantUserOptimizeAccess("kermit");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "wrongPassword");

    // then
    assertThat(response.getStatus(), is(401));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, containsString(INVALID_CREDENTIALS_ERROR_MESSAGE));
    assertThat(responseEntity, containsString(CONNECTION_WAS_REFUSED_ERROR));
  }

  @Test
  public void userIsAuthorizedAgainstEachEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultEngineRule.addUser("kermit", "kermit");
    defaultEngineRule.addUser("gonzo", "gonzo");
    defaultEngineRule.grantUserOptimizeAccess("kermit");
    secondEngineRule.addUser("kermit", "kermit");
    secondEngineRule.addUser("gonzo", "gonzo");
    secondEngineRule.grantUserOptimizeAccess("gonzo");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("kermit", "kermit");

    // then
    assertThat(response.getStatus(), is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));

    response = embeddedOptimizeRule.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus(), is(200));
    responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));
  }

  @Test
  public void afterRestartOfOptimizeRightImportIndexIsUsed() throws Exception {
    // given
    secondEngineRule.addUser("demo", "demo");
    secondEngineRule.grantAllAuthorizations("demo");
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();
    // we need finished user tasks
    deployAndStartUserTaskProcessForAllEngines();
    finishAllUserTasksForAllEngines();
    // as well as running activities
    deployAndStartUserTaskProcessForAllEngines();
    deployAndStartDecisionDefinitionForAllEngines();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    // then
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(TIMESTAMP_BASED_IMPORT_INDEX_TYPE);

    assertThat(searchResponse.getHits().getTotalHits(), is(16L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String typeName = searchHit.getSourceAsMap().get(ES_TYPE_INDEX_REFERS_TO).toString();
      String timestampOfLastEntity = searchHit.getSourceAsMap().get(TIMESTAMP_OF_LAST_ENTITY).toString();
      OffsetDateTime timestamp = OffsetDateTime.parse(
        timestampOfLastEntity,
        embeddedOptimizeRule.getDateTimeFormatter()
      );
      assertThat(
        "Timestamp for " + typeName + " should be recent",
        timestamp,
        greaterThan(OffsetDateTime.now().minusHours(1))
      );
    }
  }

  public void finishAllUserTasksForAllEngines() {
    defaultEngineRule.finishAllUserTasks();
    secondEngineRule.finishAllUserTasks();
  }

  private void deployAndStartDecisionDefinitionForAllEngines() {
    defaultEngineRule.deployAndStartDecisionDefinition();
    secondEngineRule.deployAndStartDecisionDefinition();
  }

  private void deployAndStartSimpleProcessDefinitionForAllEngines() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aStringVariable", "foo");
    defaultEngineRule.deployAndStartProcessWithVariables(
      Bpmn.createExecutableProcess("TestProcess1")
        .startEvent()
        .endEvent()
        .done(),
      variables
    );
    secondEngineRule.deployAndStartProcessWithVariables(
      Bpmn.createExecutableProcess("TestProcess2")
        .startEvent()
        .endEvent()
        .done(),
      variables
    );
  }

  private void deployAndStartUserTaskProcessForAllEngines() {
    defaultEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess("TestProcess1")
        .startEvent()
        .userTask()
        .endEvent()
        .done()
    );
    secondEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess("TestProcess2")
        .startEvent()
        .userTask()
        .endEvent()
        .done()
    );
  }

  private void addSecondEngineToConfiguration() {
    String anotherEngine = "anotherEngine";
    addEngineToConfiguration(anotherEngine);
    embeddedOptimizeRule.reloadConfiguration();
  }

  private void addEngineToConfiguration(String engineName) {
    addEngineToConfiguration(engineName, REST_ENDPOINT, false, "", "");
  }

  private void addNonExistingSecondEngineToConfiguration() {
    addEngineToConfiguration("notExistingEngine", "http://localhost:9999/engine-rest", false, "", "");
    embeddedOptimizeRule.reloadConfiguration();
  }

  private void addSecureEngineToConfiguration() {
    addEngineToConfiguration("anotherEngine", SECURE_REST_ENDPOINT, true, "admin", "admin");
  }

  private void addEngineToConfiguration(String engineName, String restEndpoint, boolean withAuthentication,
                                        String username, String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = constructEngineAuthenticationConfiguration(
      withAuthentication,
      username,
      password
    );

    EngineConfiguration anotherEngineConfig = new EngineConfiguration();
    anotherEngineConfig.setName(engineName);
    anotherEngineConfig.setRest(restEndpoint);
    anotherEngineConfig.setAuthentication(engineAuthenticationConfiguration);
    configurationService
      .getConfiguredEngines()
      .put(SECOND_ENGINE_ALIAS, anotherEngineConfig);
  }

  private EngineAuthenticationConfiguration constructEngineAuthenticationConfiguration(boolean withAuthentication,
                                                                                       String username,
                                                                                       String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = new EngineAuthenticationConfiguration();
    engineAuthenticationConfiguration.setEnabled(withAuthentication);
    engineAuthenticationConfiguration.setPassword(password);
    engineAuthenticationConfiguration.setUser(username);
    return engineAuthenticationConfiguration;
  }
}
