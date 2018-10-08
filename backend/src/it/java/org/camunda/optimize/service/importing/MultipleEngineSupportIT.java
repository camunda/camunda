package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.*;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_OF_LAST_ENTITY;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.MatcherAssert.assertThat;
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
  public void allProcessDefinitionsAreImported() {
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
  public void allProcessInstancesEventAndVariablesAreImported() {
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
  public void allProcessInstancesEventAndVariablesAreImportedWithAuthentication() {
    // given
    secondEngineRule.addUser("admin", "admin");
    secondEngineRule.grantAllAuthorizations("admin");
    addSecureEngineToConfiguration();
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
    deployAndStartUserTaskProcessForAllEngines();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    // then
    SearchResponse searchResponse = elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(TIMESTAMP_BASED_IMPORT_INDEX_TYPE))
      .setTypes(TIMESTAMP_BASED_IMPORT_INDEX_TYPE)
      .setQuery(matchAllQuery())
      .setSize(100)
      .get();

    assertThat(searchResponse.getHits().getTotalHits(), is(8L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String timestampOfLastEntity = searchHit.getSourceAsMap().get(TIMESTAMP_OF_LAST_ENTITY).toString();
      OffsetDateTime timestamp = OffsetDateTime.parse(timestampOfLastEntity, embeddedOptimizeRule.getDateTimeFormatter());
      assertThat(timestamp, greaterThan(OffsetDateTime.now().minusHours(1)));
    }
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

  private void addNonExistingSecondEngineToConfiguration() {
    addEngineToConfiguration("notExistingEngine");
  }

  private void addEngineToConfiguration(String engineName) {
    addEngineToConfiguration(engineName, REST_ENDPOINT, false, "", "");
  }

  private void addSecureEngineToConfiguration() {
    addEngineToConfiguration("anotherEngine", SECURE_REST_ENDPOINT, true, "admin", "admin");
  }

  private void addEngineToConfiguration(String engineName, String restEndpoint, boolean withAuthentication, String username, String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = constructEngineAuthenticationConfiguration(withAuthentication, username, password);

    EngineConfiguration anotherEngineConfig = new EngineConfiguration();
    anotherEngineConfig.setName(engineName);
    anotherEngineConfig.setRest(restEndpoint);
    anotherEngineConfig.setAuthentication(engineAuthenticationConfiguration);
    configurationService
      .getConfiguredEngines()
      .put(SECOND_ENGINE_ALIAS, anotherEngineConfig);
  }

  private EngineAuthenticationConfiguration constructEngineAuthenticationConfiguration(boolean withAuthentication, String username, String password) {
    EngineAuthenticationConfiguration engineAuthenticationConfiguration = new EngineAuthenticationConfiguration();
    engineAuthenticationConfiguration.setEnabled(withAuthentication);
    engineAuthenticationConfiguration.setPassword(password);
    engineAuthenticationConfiguration.setUser(username);
    return engineAuthenticationConfiguration;
  }
}
