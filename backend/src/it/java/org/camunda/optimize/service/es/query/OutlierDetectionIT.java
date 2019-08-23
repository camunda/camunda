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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

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
    // given
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

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    // assuming normal distribution, left and right outliers should be of the same percentile
    final FindingsDto activity1Findings = outlierTest.get("testActivity1");
    assertThat(activity1Findings.getHigherOutlier().isPresent(), is(true));
    assertThat(activity1Findings.getHigherOutlier().get().getBoundValue(), is(39486L));
    assertThat(
      activity1Findings.getHigherOutlier().get().getPercentile(),
      closeTo(activity1Findings.getLowerOutlier().get().getPercentile(), 0.00001)
    );
    assertThat(activity1Findings.getLowerOutlier().isPresent(), is(true));
    assertThat(activity1Findings.getLowerOutlier().get().getBoundValue(), is(513L));
    assertThat(
      activity1Findings.getLowerOutlier().get().getCount(), is(activity1Findings.getHigherOutlier().get().getCount())
    );
    assertThat(
      activity1Findings.getOutlierCount(),
      is(activity1Findings.getLowerOutlier().get().getCount() + activity1Findings.getHigherOutlier().get().getCount())
    );
    assertThat(activity1Findings.getLowerOutlierHeat(), is(1.0D));
    assertThat(activity1Findings.getHigherOutlierHeat(), is(greaterThan(0.0D)));
    assertThat(activity1Findings.getHeat(), is(greaterThan(0.0D)));

    final FindingsDto activity2Findings = outlierTest.get("testActivity2");
    // second activity only has higher outliers
    assertThat(activity2Findings.getLowerOutlier().isPresent(), is(false));
    assertThat(activity2Findings.getHigherOutlier().isPresent(), is(true));
    assertThat(activity2Findings.getOutlierCount(), is(activity2Findings.getHigherOutlier().get().getCount()));
    assertThat(activity2Findings.getHigherOutlierHeat(), is(greaterThan(0.0D)));
    assertThat(activity2Findings.getLowerOutlierHeat(), is(0.0D));
    // second activity has way more higher outliers and thus heat than activity 1
    assertThat(activity2Findings.getHeat(), is(greaterThan(activity1Findings.getHeat())));
    assertThat(activity2Findings.getHigherOutlierHeat(), is(greaterThan(activity1Findings.getHigherOutlierHeat())));
    assertThat(activity2Findings.getLowerOutlierHeat(), is(lessThan(activity1Findings.getLowerOutlierHeat())));
  }

  @Test
  public void noOutliersFoundTest() throws SQLException {
    // given
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

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    assertThat(outlierTest.get("testActivity").getOutlierCount(), is(0L));
  }

  @Test
  public void singleOutlierFoundTest() throws SQLException {
    // given
    final String activityId = "testActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    // a couple of normally distributed instances
    startPIsDistributedByDuration(
      processDefinition, new Gaussian(10 / 2, 15), 5, activityId
    );
    // a single higher outlier instance
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(
      processInstance.getId(),
      activityId,
      Math.round(Math.exp(NUMBER_OF_DATAPOINTS) * 100_000)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    assertThat(outlierTest.get(activityId).getOutlierCount(), is(1L));
    assertThat(outlierTest.get(activityId).getLowerOutlierHeat(), is(0.0D));
    assertThat(outlierTest.get(activityId).getHigherOutlierHeat(), is(greaterThan(0.0D)));
    assertThat(outlierTest.get(activityId).getLowerOutlier().isPresent(), is(false));
    assertThat(outlierTest.get(activityId).getHigherOutlier().isPresent(), is(true));
    assertThat(outlierTest.get(activityId).getHigherOutlier().get().getCount(), is(1L));
  }

  @Test
  public void allDurationsSameValueNoOutliers() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("testActivity"));

    startPIsDistributedByDuration(
      processDefinition,
      // one data point => no distribution
      new Gaussian(1, 1),
      1,
      "testActivity"
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    final FindingsDto testActivity = outlierTest.get("testActivity");
    assertThat(testActivity.getOutlierCount(), is(0L));
    assertThat(testActivity.getLowerOutlier().isPresent(), is(false));
    assertThat(testActivity.getHigherOutlier().isPresent(), is(false));
    assertThat(testActivity.getLowerOutlierHeat(), is(0.0D));
    assertThat(testActivity.getHigherOutlierHeat(), is(0.0D));
    assertThat(testActivity.getHeat(), is(0.0D));
  }

  @Test
  public void moreThan10Activities() throws SQLException {
    // 10 is the default terms limit of elasticearch, this ensures the default does not apply

    // given
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(
      getBpmnModelInstance(IntStream.range(0, 10).mapToObj(i -> "testActivity" + i).toArray(String[]::new))
    );

    // one instance is suffice, we just need data from each activity, having in total >10 activities
    final ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest("outlierTest", Collections.singletonList("1"), Collections.singletonList(null))
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    assertThat(outlierTest.size(), is(12));
  }

  @Test
  public void durationChartNormalDistributionTest() throws SQLException {
    // given
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

    // when
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        "outlierTest",
        Collections.singletonList("1"),
        "chartTestActivity",
        Collections.singletonList(null)
      )
      .executeAndReturnList(DurationChartEntryDto.class, 200);

    // then
    for (int i = 0; i < durationChart.size() / 2; i++) {
      assertThat(durationChart.get(i).getValue() <= durationChart.get(i + 1).getValue(), is(true));
    }
    for (int i = durationChart.size() / 2; i < durationChart.size(); i++) {
      assertThat(durationChart.get(i - 1).getValue() >= durationChart.get(i).getValue(), is(true));
    }
  }

  @Test
  public void durationChartNormalDistributionWithOutlierMarkingTest() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("chartTestActivity"));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2, 12),
      NUMBER_OF_DATAPOINTS,
      "chartTestActivity"
    );
    final long lowerOutlierBound = 513L;
    final long higherOutlierBound = 39486L;

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        "outlierTest",
        Collections.singletonList("1"),
        "chartTestActivity",
        Collections.singletonList(null),
        lowerOutlierBound,
        higherOutlierBound
      )
      .executeAndReturnList(DurationChartEntryDto.class, 200);

    // then
    for (int i = 0; i < durationChart.size() / 2; i++) {
      assertThat(durationChart.get(i).isOutlier(), is(durationChart.get(i).getKey() <= lowerOutlierBound));
    }
    for (int i = durationChart.size() / 2; i < durationChart.size(); i++) {
      assertThat(durationChart.get(i).isOutlier(), is(durationChart.get(i).getKey() >= higherOutlierBound));
    }
  }

  @Test
  public void durationChartOnePerBucketTest() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance("chartTestActivity"));

    for (int i = 0; i < 80; i++) {
      ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
      engineDatabaseRule.changeActivityDuration(processInstance.getId(), "chartTestActivity", i * 1000);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        "outlierTest",
        Collections.singletonList("1"),
        "chartTestActivity",
        Collections.singletonList(null)
      )
      .executeAndReturnList(DurationChartEntryDto.class, 200);

    // then
    assertThat(durationChart.get(0).getValue(), is(1L));
    assertThat(durationChart.stream().allMatch(e -> e.getValue().equals(durationChart.get(0).getValue())), is(true));

  }

  private void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition,
                                             Gaussian gaussian,
                                             int numberOfDataPoints,
                                             String... activityId) throws SQLException {

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
