package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.rest.StatusRestServiceIT.ENGINE_ALIAS;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class MultipleEngineSupportIT {

  public static final String REST_ENDPOINT = "http://localhost:48080/engine-rest";
  private static final String SECURE_REST_ENDPOINT = "http://localhost:48080/engine-rest-secure";
  private final String SECOND_ENGINE_ALIAS = "secondTestEngine";
  public EngineIntegrationRule defaultEngineRule = new EngineIntegrationRule();
  public EngineIntegrationRule secondEngineRule =
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
    configurationService.getConfiguredEngines().get(ENGINE_ALIAS).getAuthentication().setAccessGroup("");
    configurationService.getConfiguredEngines().remove(SECOND_ENGINE_ALIAS);
    embeddedOptimizeRule.reloadConfiguration();
  }

  @Test
  public void importProgressReporterStartAndEndImportState() throws Exception {
    // given
    addSecondEngineToConfiguration();

    // when
    defaultEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess()
        .startEvent()
        .endEvent()
        .done()
    );

    secondEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess()
        .startEvent()
        .endEvent()
        .done()
    );

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(0L));

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));
  }

  @Test
  public void importProgressReflectsIfImportProgressOfAllEngines() throws Exception {
    // given
    addSecondEngineToConfiguration();
    defaultEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess()
        .startEvent()
        .endEvent()
        .done()
    );

    // when
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    secondEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess()
        .startEvent()
        .endEvent()
        .done()
    );
    embeddedOptimizeRule.updateImportIndex();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(50L));
  }

  @Test
  public void importProgressReportWorksEvenIfOneEngineIsDown() throws OptimizeException {
    // given
    addNonExistingSecondEngineToConfiguration();
    embeddedOptimizeRule.getConfigurationService().setMaximumBackoff(1l);
    embeddedOptimizeRule.reloadConfiguration();
    defaultEngineRule.deployAndStartProcess(
      Bpmn.createExecutableProcess()
        .startEvent()
        .endEvent()
        .done()
    );

    // when
    embeddedOptimizeRule.scheduleImport();
    embeddedOptimizeRule.scheduleImport();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThat(embeddedOptimizeRule.getProgressValue(), is(100L));

    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    embeddedOptimizeRule.getConfigurationService().setMaximumBackoff(5l);
  }

  @Test
  public void connectionStatusCheck() throws Exception {
    // given
    addSecondEngineToConfiguration();

    // when
    Response response = embeddedOptimizeRule.target("status/connection")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    ConnectionStatusDto actual =
      response.readEntity(ConnectionStatusDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.isConnectedToElasticsearch(), is(true));
    assertThat(actual.getEngineConnections(), is(notNullValue()));
    assertThat(actual.getEngineConnections().get(ENGINE_ALIAS), is(true));
    assertThat(actual.getEngineConnections().get(SECOND_ENGINE_ALIAS), is(true));
  }

  @Test
  public void connectionStatusCheckWithOneEngineDown() throws Exception {
    // given
    addNonExistingSecondEngineToConfiguration();
    embeddedOptimizeRule.reloadConfiguration();

    // when
    Response response = embeddedOptimizeRule.target("status/connection")
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    ConnectionStatusDto actual =
      response.readEntity(ConnectionStatusDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.isConnectedToElasticsearch(), is(true));
    assertThat(actual.getEngineConnections(), is(notNullValue()));
    assertThat(actual.getEngineConnections().get(ENGINE_ALIAS), is(true));
    assertThat(actual.getEngineConnections().get(SECOND_ENGINE_ALIAS), is(false));
  }

  @Test
  public void allProcessDefinitionXmlsAreImported() throws Exception {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(configurationService.getProcessDefinitionXmlType()))
      .setTypes(configurationService.getProcessDefinitionXmlType())
      .setQuery(matchAllQuery())
      .setSize(100)
      .get();

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionId = (String) searchHit.getSourceAsMap().get(PROCESS_DEFINITION_ID);
      String processDefinitionKey = getKeyForProcessDefinitionId(processDefinitionId);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
    }
  }

  private String getKeyForProcessDefinitionId(String processDefinitionId) {
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(configurationService.getProcessDefinitionType()))
      .setTypes(configurationService.getProcessDefinitionType())
      .setQuery(termQuery(PROCESS_DEFINITION_ID, processDefinitionId))
      .setSize(100)
      .get();
    assertThat(searchResponse.getHits().getTotalHits(), is(1L));
    return (String) searchResponse.getHits().getHits()[0].getSourceAsMap().get(PROCESS_DEFINITION_KEY);
  }

  @Test
  public void allProcessDefinitionsAreImported() throws Exception {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(configurationService.getProcessDefinitionType()))
      .setTypes(configurationService.getProcessDefinitionType())
      .setQuery(matchAllQuery())
      .setSize(100)
      .get();

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
  public void allProcessInstancesEventAndVariablesAreImported() throws Exception {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(matchAllQuery())
      .setSize(100)
      .get();

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
  public void allProcessInstancesEventAndVariablesAreImportedWithAuthentication() throws Exception {
    // given
    secondEngineRule.addUser("demo", "demo");
    addSecureEngineToConfiguration("anotherEngine");
    embeddedOptimizeRule.reloadConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(matchAllQuery())
      .setSize(100)
      .get();

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertImportResults(searchResponse, allowedProcessDefinitionKeys);
  }

  @Test
  public void optimizeGroupsFromEveryEngineAreAccepted() {

    // given
    addSecondEngineToConfiguration();
    EngineConfiguration engineConfiguration =
      configurationService.getConfiguredEngines().get(ENGINE_ALIAS);
    engineConfiguration.getAuthentication().setAccessGroup("optimizeGroup1");
    engineConfiguration =
      configurationService.getConfiguredEngines().get(SECOND_ENGINE_ALIAS);
    engineConfiguration.getAuthentication().setAccessGroup("optimizeGroup2");

    // given
    defaultEngineRule.createGroup("optimizeGroup1", "Optimize Access Group", "Foo type");
    defaultEngineRule.addUser("demo", "demo");
    defaultEngineRule.addUserToGroup("demo", "optimizeGroup1");
    defaultEngineRule.addUser("kermit", "frog");

    secondEngineRule.createGroup("optimizeGroup2", "Optimize Access Group", "Foo type");
    secondEngineRule.addUser("gonzo", "gonzo");
    secondEngineRule.addUserToGroup("gonzo", "optimizeGroup2");
    secondEngineRule.addUser("scooter", "scooter");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("demo", "demo");

    // then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));

    response = embeddedOptimizeRule.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus(),is(200));
    responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));

    // when
    response = embeddedOptimizeRule.authenticateUserRequest("kermit", "frog");

    // then
    assertThat(response.getStatus(),is(401));

    // when
    response = embeddedOptimizeRule.authenticateUserRequest("scooter", "scooter");

    // then
    assertThat(response.getStatus(),is(401));

  }

  @Test
  public void userIsAuthenticatedAgainstEachEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultEngineRule.addUser("demo", "demo");
    secondEngineRule.addUser("gonzo", "gonzo");

    // when
    Response response = embeddedOptimizeRule.authenticateUserRequest("demo", "demo");

    // then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));

    response = embeddedOptimizeRule.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus(),is(200));
    responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  @Test
  public void afterRestartOfOptimizeRightImportIndexIsUsed() throws Exception {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    // then
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(configurationService.getProcessDefinitionImportIndexType()))
      .setTypes(configurationService.getProcessDefinitionImportIndexType())
      .setQuery(matchAllQuery())
      .setSize(100)
      .get();

    List<String> allowedProcessDefinitionKeys = new ArrayList<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    allowedProcessDefinitionKeys.add("TestProcess2");
    allowedProcessDefinitionKeys.add("TestProcess2");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertThat(searchResponse.getHits().getTotalHits(), is(8L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionId = ((Map)searchHit.getSourceAsMap().get("currentProcessDefinition")).get("processDefinitionId").toString();
      String processDefinitionKey = getKeyForProcessDefinitionId(processDefinitionId);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
    }
    assertThat(allowedProcessDefinitionKeys.isEmpty(), is(true));
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

  private void addSecondEngineToConfiguration() {
    String anotherEngine = "anotherEngine";
    addEngineToConfiguration(anotherEngine);
    embeddedOptimizeRule.reloadConfiguration();
  }

  private void addNonExistingSecondEngineToConfiguration() {
    addEngineToConfiguration("notExistingEngine");
  }

  private void addEngineToConfiguration(String engineName) {
    addEngineToConfiguration(engineName, REST_ENDPOINT, false, "", "");
  }

  private void addSecureEngineToConfiguration(String engineName) {
    addEngineToConfiguration(engineName, SECURE_REST_ENDPOINT, true, "demo", "demo");
  }

  private void addEngineToConfiguration(String engineName, String restEndpoint, boolean withAuthentication, String username, String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = constructEngineAuthenticationConfiguration(withAuthentication, username, password);

    EngineConfiguration anotherEngineConfig = new EngineConfiguration();
    anotherEngineConfig.setEnabled(true);
    anotherEngineConfig.setName(engineName);
    anotherEngineConfig.setRest(restEndpoint);
    anotherEngineConfig.setAuthentication(engineAuthenticationConfiguration);
    configurationService
      .getConfiguredEngines()
      .put(SECOND_ENGINE_ALIAS, anotherEngineConfig);
  }

  private EngineAuthenticationConfiguration constructEngineAuthenticationConfiguration(boolean withAuthentication, String username, String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = new EngineAuthenticationConfiguration();
    engineAuthenticationConfiguration.setAccessGroup("");
    engineAuthenticationConfiguration.setEnabled(withAuthentication);
    engineAuthenticationConfiguration.setPassword(password);
    engineAuthenticationConfiguration.setUser(username);
    return engineAuthenticationConfiguration;
  }
}
