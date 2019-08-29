/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class OutlierAnalysisIT {
  private static final int NUMBER_OF_DATAPOINTS = 40;
  private static final int NUMBER_OF_DATAPOINTS_FOR_CHART = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
  private static final String VARIABLE_1_NAME = "var1";
  private static final String VARIABLE_2_NAME = "var2";
  private static final String VARIABLE_VALUE_OUTLIER = "outlier";
  private static final String VARIABLE_VALUE_NORMAL = "normal";
  private static final String FLOW_NODE_ID_TEST = "testActivity";
  private static final String PROCESS_DEFINITION_KEY = "outlierTest";
  private static final Random RANDOM = new Random();
  // this particular value is obtained from precalculation
  // given the distribution and outlier setup created by #createNormalDistributionAnd3Outliers
  private static final long SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND = 30738L;

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
    final String testActivity1 = "testActivity1";
    final String testActivity2 = "testActivity2";
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(testActivity1, testActivity2));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2, 12),
      NUMBER_OF_DATAPOINTS,
      testActivity1,
      testActivity2
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null)
      )
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    // assuming normal distribution, left and right outliers should be of the same percentile
    final FindingsDto activity1Findings = outlierTest.get(testActivity1);
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

    final FindingsDto activity2Findings = outlierTest.get(testActivity2);
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
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    // high sigma value, so that no process instances are out of 2*sigma bounds
    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2, 15),
      NUMBER_OF_DATAPOINTS,
      FLOW_NODE_ID_TEST
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null)
      )
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getOutlierCount(), is(0L));
  }

  @Test
  public void singleOutlierFoundTest() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    // a couple of normally distributed instances
    startPIsDistributedByDuration(
      processDefinition, new Gaussian(10 / 2, 15), 5, FLOW_NODE_ID_TEST
    );
    // a single higher outlier instance
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), FLOW_NODE_ID_TEST, 100_000);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null)
      )
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getOutlierCount(), is(1L));
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getLowerOutlierHeat(), is(0.0D));
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getHigherOutlierHeat(), is(greaterThan(0.0D)));
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getLowerOutlier().isPresent(), is(false));
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getHigherOutlier().isPresent(), is(true));
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getHigherOutlier().get().getCount(), is(1L));
  }

  @Test
  public void allDurationsSameValueNoOutliers() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    startPIsDistributedByDuration(
      processDefinition,
      // one data point => no distribution
      new Gaussian(1, 1),
      1,
      FLOW_NODE_ID_TEST
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null)
      )
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    final FindingsDto testActivity = outlierTest.get(FLOW_NODE_ID_TEST);
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
      getBpmnModelInstance(IntStream.range(0, 10).mapToObj(i -> FLOW_NODE_ID_TEST + i).toArray(String[]::new))
    );

    // one instance is suffice, we just need data from each activity, having in total >10 activities
    final ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    HashMap<String, FindingsDto> outlierTest = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeOutliersRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null)
      )
      .execute(new TypeReference<HashMap<String, FindingsDto>>() {
      });

    // then
    assertThat(outlierTest.size(), is(12));
  }

  @Test
  public void durationChartNormalDistributionTest() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS_FOR_CHART / 2, 15),
      NUMBER_OF_DATAPOINTS_FOR_CHART,
      FLOW_NODE_ID_TEST
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null),
        FLOW_NODE_ID_TEST
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
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2, 12),
      NUMBER_OF_DATAPOINTS,
      FLOW_NODE_ID_TEST
    );
    final long lowerOutlierBound = 513L;
    final long higherOutlierBound = 39486L;

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        FLOW_NODE_ID_TEST,
        Collections.singletonList(null),
        lowerOutlierBound,
        higherOutlierBound
      )
      .executeAndReturnList(DurationChartEntryDto.class, 200);

    // then
    for (int i = 0; i < durationChart.size() / 2; i++) {
      assertThat(durationChart.get(i).isOutlier(), is(durationChart.get(i).getKey() < lowerOutlierBound));
    }
    for (int i = durationChart.size() / 2; i < durationChart.size(); i++) {
      assertThat(durationChart.get(i).isOutlier(), is(durationChart.get(i).getKey() > higherOutlierBound));
    }
  }

  @Test
  public void durationChartOnePerBucketTest() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    for (int i = 0; i < 80; i++) {
      ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
      engineDatabaseRule.changeActivityDuration(processInstance.getId(), FLOW_NODE_ID_TEST, i * 1000);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<DurationChartEntryDto> durationChart = embeddedOptimizeRule.getRequestExecutor()
      .buildFlowNodeDurationChartRequest(
        PROCESS_DEFINITION_KEY,
        Collections.singletonList("1"),
        Collections.singletonList(null),
        FLOW_NODE_ID_TEST
      )
      .executeAndReturnList(DurationChartEntryDto.class, 200);

    // then
    assertThat(durationChart.get(0).getValue(), is(1L));
    assertThat(durationChart.stream().allMatch(e -> e.getValue().equals(durationChart.get(0).getValue())), is(true));

  }

  @Test
  public void significantOutlierVariableValues() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));
    // a couple of normally distributed instances
    createNormalDistributionAnd3Outliers(processDefinition, VARIABLE_VALUE_OUTLIER);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableTermDto> variableTermDtosActivity = embeddedOptimizeRule.getRequestExecutor()
      .buildSignificantOutlierVariableTermsRequest(
        processDefinition.getKey(),
        Collections.singletonList("1"),
        Collections.singletonList(null),
        FLOW_NODE_ID_TEST,
        null,
        SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND
      )
      .executeAndReturnList(VariableTermDto.class, 200);

    // then
    assertThat(variableTermDtosActivity.size(), is(1));
    final VariableTermDto variableTermDto = variableTermDtosActivity.get(0);
    assertThat(variableTermDto.getVariableName(), is(VARIABLE_2_NAME));
    assertThat(variableTermDto.getVariableTerm(), is(VARIABLE_VALUE_OUTLIER));
    assertThat(variableTermDto.getInstanceCount(), is(3L));
  }

  @Test
  public void noSignificantOutlierVariableValues() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));
    createNormalDistributionAnd3Outliers(processDefinition, VARIABLE_VALUE_NORMAL);
    // this particular value is obtained from precalculation, given the distribution and outlier setup
    final long activityHigherOutlierBound = SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND;

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableTermDto> variableTermDtosActivity = embeddedOptimizeRule.getRequestExecutor()
      .buildSignificantOutlierVariableTermsRequest(
        processDefinition.getKey(),
        Collections.singletonList("1"),
        Collections.singletonList(null),
        FLOW_NODE_ID_TEST,
        null,
        activityHigherOutlierBound
      )
      .executeAndReturnList(VariableTermDto.class, 200);

    // then
    assertThat(variableTermDtosActivity.size(), is(0));
  }

  @Test
  public void noOutliersResultsInNotFoundOnVariables() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS_FOR_CHART / 2, 15),
      NUMBER_OF_DATAPOINTS_FOR_CHART,
      FLOW_NODE_ID_TEST
    );

    // high duration for which there are no instances
    final long activityHigherOutlierBound = 100_000L;

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.getRequestExecutor()
      .buildSignificantOutlierVariableTermsRequest(
        processDefinition.getKey(),
        Collections.singletonList("1"),
        Collections.singletonList(null),
        FLOW_NODE_ID_TEST,
        null,
        activityHigherOutlierBound
      )
      // then
      .executeAndReturnList(VariableTermDto.class, 404);
  }

  @Test
  public void significantOutlierVariableValuesProcessInstanceIdExport() throws SQLException, IOException {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    List<String> expectedOutlierInstanceIds = createNormalDistributionAnd3Outliers(
      processDefinition, VARIABLE_VALUE_OUTLIER
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildSignificantOutlierVariableTermsInstanceIdsRequest(
        processDefinition.getKey(),
        Collections.singletonList("1"),
        Collections.singletonList(null),
        FLOW_NODE_ID_TEST,
        null,
        SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND,
        VARIABLE_2_NAME,
        VARIABLE_VALUE_OUTLIER
      )
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    final String csvContent = getResponseContentAsString(response);
    final String[] lines = csvContent.split("\\n");
    assertThat(lines.length, is(4));
    assertThat(lines[0], is("\"processInstanceId\""));
    assertThat(
      Arrays.asList(ArrayUtils.subarray(lines, 1, 4)),
      containsInAnyOrder(expectedOutlierInstanceIds.stream().map(s -> "\"" + s + "\"").toArray())
    );
  }

  private List<String> createNormalDistributionAnd3Outliers(final ProcessDefinitionEngineDto processDefinition,
                                                            final String outlierVariable2Value)
    throws SQLException {
    // a couple of normally distributed instances
    startPIsDistributedByDuration(
      processDefinition, new Gaussian(10 / 2, 15), 5, FLOW_NODE_ID_TEST
    );

    List<String> outlierInstanceIds = new ArrayList<>();
    // 3 higher outlier instance
    // 3 is the minDoc count for which terms are considered to eliminate high cardinality variables
    for (int i = 0; i < 3; i++) {
      ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(
        processDefinition.getId(),
        // VAR2 has the same value as all non outliers
        ImmutableMap.of(VARIABLE_1_NAME, RANDOM.nextInt(), VARIABLE_2_NAME, outlierVariable2Value)
      );
      engineDatabaseRule.changeActivityDuration(processInstance.getId(), FLOW_NODE_ID_TEST, 100_000);
      outlierInstanceIds.add(processInstance.getId());
    }

    return outlierInstanceIds;
  }

  private void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition,
                                             Gaussian gaussian,
                                             int numberOfDataPoints,
                                             String firstActivityId) throws SQLException {
    startPIsDistributedByDuration(processDefinition, gaussian, numberOfDataPoints, firstActivityId, null);
  }

  private void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition,
                                             Gaussian gaussian,
                                             int numberOfDataPoints,
                                             String firstActivityId,
                                             String secondActivityId) throws SQLException {
    startPIsDistributedByDuration(
      processDefinition,
      gaussian,
      numberOfDataPoints,
      firstActivityId,
      secondActivityId,
      0L
    );
  }

  private void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition,
                                             Gaussian gaussian,
                                             int numberOfDataPoints,
                                             String firstActivityId,
                                             String secondActivityId,
                                             long higherDurationOutlierBoundary) throws SQLException {
    for (int i = 0; i <= numberOfDataPoints; i++) {
      for (int x = 0; x <= gaussian.value(i) * 1000; x++) {
        final long firstActivityDuration = i * 1000L;
        // a more "stretched" distribution on the second activity
        final long secondActivityDuration = Math.round(firstActivityDuration + Math.exp(i) * 1000);
        ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(
          processDefinition.getId(),
          ImmutableMap.of(
            VARIABLE_1_NAME,
            RANDOM.nextInt(),
            VARIABLE_2_NAME,
            secondActivityId != null && secondActivityDuration > higherDurationOutlierBoundary ?
              VARIABLE_VALUE_OUTLIER : VARIABLE_VALUE_NORMAL
          )
        );
        engineDatabaseRule.changeActivityDuration(processInstance.getId(), firstActivityId, firstActivityDuration);
        engineDatabaseRule.changeActivityDuration(processInstance.getId(), secondActivityId, secondActivityDuration);
      }
    }
  }

  private BpmnModelInstance getBpmnModelInstance(String... activityId) {
    StartEventBuilder builder = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .name("aProcessName")
      .startEvent("start");
    for (String activity : activityId) {
      builder.serviceTask(activity)
        .camundaExpression("${true}");
    }
    return builder.endEvent("end").done();
  }

}
