package org.camunda.optimize.service.cleanup;

import com.google.common.collect.Lists;
import org.apache.commons.collections.ListUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.camunda.optimize.service.util.configuration.CleanupMode;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.ProcessDefinitionCleanupConfiguration;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class OptimizeCleanupServiceIT {
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void verifyCleanupDisabledByDefault() {
    assertThat(getCleanupConfiguration().getEnabled(), is(false));
    assertThat(embeddedOptimizeRule.getCleanupService().isScheduledToRun(), is(false));
  }

  @Test
  public void testCleanupIsScheduledSuccessfully() {
    embeddedOptimizeRule.getCleanupService().startCleanupScheduling();
    try {
      assertThat(embeddedOptimizeRule.getCleanupService().isScheduledToRun(), is(true));
    } finally {
      embeddedOptimizeRule.getCleanupService().stopCleanupScheduling();
    }
  }

  @Test
  public void testCleanupScheduledStoppedSuccessfully() {
    embeddedOptimizeRule.getCleanupService().startCleanupScheduling();
    embeddedOptimizeRule.getCleanupService().stopCleanupScheduling();
    assertThat(embeddedOptimizeRule.getCleanupService().isScheduledToRun(), is(false));
  }

  @Test
  public void testCleanupWithProcessInstanceDelete() throws SQLException {
    // given
    getCleanupConfiguration().setDefaultMode(CleanupMode.ALL);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    embeddedOptimizeRule.getCleanupService().runCleanup();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    assertProcessInstanceDataCompleteInEs(Collections.emptyList());
  }

  @Test
  public void testCleanupWithProcessInstanceDeleteVerifyThatNewOnesAreUnaffected() throws SQLException {
    // given
    getCleanupConfiguration().setDefaultMode(CleanupMode.ALL);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<String> unaffectedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    embeddedOptimizeRule.getCleanupService().runCleanup();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    assertProcessInstanceDataCompleteInEs(unaffectedProcessDefinitionsIds);
  }

  @Test
  public void testCleanupProcessInstanceVariablesCleared() throws SQLException {
    // given
    getCleanupConfiguration().setDefaultMode(CleanupMode.VARIABLES);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    embeddedOptimizeRule.getCleanupService().runCleanup();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    assertVariablesEmptyInProcessInstances(clearedProcessDefinitionsIds);
  }

  @Test
  public void testCleanupProcessInstanceVariablesClearedVerifyThatNewOnesAreUnaffected() throws SQLException {
    // given
    getCleanupConfiguration().setDefaultMode(CleanupMode.VARIABLES);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<String> unaffectedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    embeddedOptimizeRule.getCleanupService().runCleanup();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //then
    assertVariablesEmptyInProcessInstances(clearedProcessDefinitionsIds);
    assertProcessInstanceDataCompleteInEs(unaffectedProcessDefinitionsIds);
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinitionNoInstancesCleaned()
    throws SQLException {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      configuredKey,
      new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
    );
    // and deploy processes with different keys
    final List<String> processDefinitionsWithEndTimeLessThanTtl = deployTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<String> unaffectedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    OptimizeConfigurationException expectedException = null;
    try {
      embeddedOptimizeRule.getCleanupService().runCleanup();
    } catch (OptimizeConfigurationException e) {
      expectedException = e;
    }
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // all data is still there
    MatcherAssert.assertThat(expectedException, CoreMatchers.is((notNullValue())));
    assertProcessInstanceDataCompleteInEs(
      ListUtils.union(processDefinitionsWithEndTimeLessThanTtl, unaffectedProcessDefinitionsIds)
    );
  }

  private ProcessInstanceEngineDto startNewProcessWithSameProcessDefinitionId(String processDefinitionId) {
    return engineRule.startProcessInstance(processDefinitionId, VariableTestUtil.createAllPrimitiveTypeVariables());
  }

  private List<String> deployTwoProcessInstancesWithEndTimeLessThanTtl() throws SQLException {
    return deployTwoProcessInstancesWithEndTime(
      OffsetDateTime.now().minus(getCleanupConfiguration().getDefaultTtl()).minusSeconds(1)
    );
  }

  private List<String> deployTwoProcessInstancesWithEndTime(OffsetDateTime endTime) throws SQLException {
    final ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
    final ProcessInstanceEngineDto secondProcInst = startNewProcessWithSameProcessDefinitionId(
      firstProcInst.getDefinitionId()
    );

    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    procInstEndDateUpdates.put(firstProcInst.getId(), endTime);
    procInstEndDateUpdates.put(secondProcInst.getId(), endTime);
    engineDatabaseRule.updateProcessInstanceEndDates(procInstEndDateUpdates);

    return Lists.newArrayList(firstProcInst.getId(), secondProcInst.getId());
  }


  private void assertVariablesEmptyInProcessInstances(List<String> processIds) {

    SearchResponse idsResp = getProcessInstancesById(processIds);

    assertThat(idsResp.getHits().getTotalHits(), is(Long.valueOf(processIds.size())));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      for (String variableFieldName : ProcessVariableHelper.getAllVariableTypeFieldLabels()) {
        assertThat(
          variableFieldName + "is empty",
          searchHit.getSourceAsMap().get(variableFieldName),
          is(Collections.emptyList())
        );
      }
    }
  }

  private SearchResponse getProcessInstancesById(List<String> processIds) {
    return elasticSearchRule.getClient()
      .prepareSearch(elasticSearchRule.getOptimizeIndex(elasticSearchRule.getProcessInstanceType()))
      .setTypes(elasticSearchRule.getProcessInstanceType())
      .setQuery(termsQuery(ProcessInstanceType.PROCESS_INSTANCE_ID, processIds))
      .setSize(100)
      .get();
  }

  private void assertProcessInstanceDataCompleteInEs(List<String> processIds) {
    SearchResponse idsResp = getProcessInstancesById(processIds);
    assertThat(idsResp.getHits().getTotalHits(), is(Long.valueOf(processIds.size())));

    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      for (String variableFieldName : ProcessVariableHelper.getAllVariableTypeFieldLabels()) {
        assertThat(
          variableFieldName + "is not empty",
          ((Collection) searchHit.getSourceAsMap().get(variableFieldName)).size(),
          is(greaterThan(0))
        );
      }
    }
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(
      processModel,
      VariableTestUtil.createAllPrimitiveTypeVariables()
    );
  }


  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration();
  }
}
