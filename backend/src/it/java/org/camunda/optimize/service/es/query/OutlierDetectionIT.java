/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.query;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

public class OutlierDetectionIT {
  private static final int NUMBER_OF_DATAPOINTS = 40;
  private static final int NUMBER_OF_DATAPOINTS_FOR_CHART = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void outlierDetectionNormalDistribution() throws SQLException {
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("testActivity1", "testActivity2"));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2, 12),
      NUMBER_OF_DATAPOINTS,
      "testActivity1",
      "testActivity2"
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // assuming normal distribution, left and right outliers should be of the same percentile
    assertThat(outlierTest.get("testActivity1").getHigherOutlier().getPercentile(),
      closeTo(outlierTest.get("testActivity1").getLowerOutlier().getPercentile(), 0.00001)
    );

    assertThat(outlierTest.get("testActivity2").getHeat() > outlierTest.get("testActivity1").getHeat(), is(true));
  }

  @Test
  public void noOutliersFoundTest() throws SQLException {
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("testActivity"));

    // high sigma value, so that no process instances are out of 2*sigma bounds
    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2, 15),
      NUMBER_OF_DATAPOINTS,
      "testActivity"
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    assertThat(outlierTest.get("testActivity").getOutlierCount(), is(0L));
  }

  @Test
  public void durationChartNormalDistributionTest() throws SQLException {
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("chartTestActivity"));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS_FOR_CHART / 2, 15),
      NUMBER_OF_DATAPOINTS_FOR_CHART,
      "chartTestActivity"
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<String> list = new ArrayList<>();
    list.add("1");
    list.add("2");
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest("outlierTest", list, "chartTestActivity", Collections.singletonList(null))
      .executeAndReturnList(DurationChartEntryDto.class, 200);
    for (int i = 0; i < durationChart.size() / 2; i++) {
      assertThat(durationChart.get(i).getValue() <= durationChart.get(i + 1).getValue(), is(true));
    }
    for (int i = durationChart.size() / 2; i < durationChart.size(); i++) {
      assertThat(durationChart.get(i - 1).getValue() >= durationChart.get(i).getValue(), is(true));
    }
  }

  @Test
  public void durationChartTest() throws SQLException {
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("chartTestActivity"));

    for (int i = 0; i < 80; i++) {
      ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
      engineDatabaseRule.changeActivityDuration(processInstance.getId(), "chartTestActivity", i * 1000);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest("outlierTest", Collections.singletonList("1"), "chartTestActivity", Collections.singletonList(null))
      .executeAndReturnList(DurationChartEntryDto.class, 200);

    assertThat(durationChart.get(0).getValue(), is(1L));
    assertThat(durationChart.stream().allMatch(e -> e.getValue().equals(durationChart.get(0).getValue())), is(true));

  }

  private void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition, Gaussian gaussian,
                                             int numberOfDataPoints,
                                             String... activityId) throws
                                                                   SQLException {

    for (int i = 0; i <= numberOfDataPoints; i++) {
      for (int x = 0; x <= gaussian.value(i) * 1000; x++) {
        ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
        engineDatabaseRule.changeActivityDuration(processInstance.getId(), activityId[0], i * 1000);
        if (activityId.length > 1) {
          // a more "stretched" distribution to get more heat on the node
          engineDatabaseRule.changeActivityDuration(
            processInstance.getId(),
            activityId[1],
            Math.round(i * 1000 + Math.exp(i) * 1000)
          );
        }
      }
    }
  }

  private BpmnModelInstance getBpmnModelInstance(String... activityId) {
    StartEventBuilder builder = Bpmn.createExecutableProcess("outlierTest")
      .name("aProcessName")
      .startEvent("start");
    for (String activity : activityId) {
      builder.serviceTask(activity)
        .camundaExpression("${true}");
    }
    return builder.endEvent("end").done();
  }
}
