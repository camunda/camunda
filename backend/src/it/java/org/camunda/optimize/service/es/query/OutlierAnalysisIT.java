/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.query;

import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.test.engine.OutlierDistributionClient.ANOTHER_FLOW_NODE_ID_TEST;
import static org.camunda.optimize.test.engine.OutlierDistributionClient.FLOW_NODE_ID_TEST;
import static org.camunda.optimize.test.engine.OutlierDistributionClient.SPLITTING_GATEWAY_LABEL;
import static org.camunda.optimize.test.engine.OutlierDistributionClient.VARIABLE_2_NAME;
import static org.camunda.optimize.test.engine.OutlierDistributionClient.VARIABLE_VALUE_NORMAL;
import static org.camunda.optimize.test.engine.OutlierDistributionClient.VARIABLE_VALUE_OUTLIER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class OutlierAnalysisIT extends AbstractIT {
  private static final int NUMBER_OF_DATAPOINTS = 40;
  private static final int NUMBER_OF_DATAPOINTS_FOR_CHART = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
  private static final String PROCESS_DEFINITION_KEY = "outlierTest";
  // this particular value is obtained from precalculation
  // given the distribution and outlier setup created by #createNormalDistributionAnd3Outliers
  private static final long SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND = 30738L;
  private static final String START_EVENT_ID = "start";
  private static final String END_EVENT_ID = "end";
  private static final String USER_TASK_ID_ONE = "user_task_1";
  private static final String USER_TASK_ID_TWO = "user_task_2";
  private static final String SERVICE_TASK_ID_ONE = "ServiceTask1";
  private static final String SERVICE_TASK_ID_TWO = "ServiceTask2";
  private static final String MANUAL_TASK_ID = "manual_task";
  private static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  private static final String MERGING_GATEWAY_ID = "mergingGateway";

  @Test
  public void outlierDetectionNormalDistribution() {
    // given
    final String testActivity1 = "testActivity1";
    final String testActivity2 = "testActivity2";
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(
        testActivity1,
        testActivity2
      ));

    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2., 12),
      NUMBER_OF_DATAPOINTS,
      testActivity1,
      testActivity2
    );

    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null)
    );

    // then
    final long expectedFlowNodeInstances = 936L;
    final FindingsDto activity1Findings = outlierTest.get(testActivity1);
    assertThat(activity1Findings.getTotalCount()).isEqualTo(expectedFlowNodeInstances);
    // assuming normal distribution, left and right outliers should be of the same percentile
    assertThat(activity1Findings.getHigherOutlier()).isPresent();
    assertThat(activity1Findings.getHigherOutlier().get().getBoundValue()).isEqualTo(39486L);
    assertThat(activity1Findings.getHigherOutlier().get().getPercentile()).isCloseTo(
      activity1Findings.getLowerOutlier().get().getPercentile(),
      within(0.00001)
    );
    assertThat(activity1Findings.getLowerOutlier()).isPresent();
    assertThat(activity1Findings.getLowerOutlier().get().getBoundValue()).isEqualTo(513L);
    assertThat(activity1Findings.getLowerOutlier().get().getCount())
      .isEqualTo(activity1Findings.getHigherOutlier().get().getCount());
    assertThat(activity1Findings.getOutlierCount())
      .isEqualTo(activity1Findings.getLowerOutlier().get().getCount() +
                   activity1Findings.getHigherOutlier().get().getCount());
    assertThat(activity1Findings.getLowerOutlierHeat()).isEqualTo(1.0D);
    assertThat(activity1Findings.getHigherOutlierHeat()).isGreaterThan(0.0D);
    assertThat(activity1Findings.getHeat()).isGreaterThan(0.0D);

    final FindingsDto activity2Findings = outlierTest.get(testActivity2);
    assertThat(activity2Findings.getTotalCount()).isEqualTo(expectedFlowNodeInstances);
    // second activity only has higher outliers
    assertThat(activity2Findings.getLowerOutlier()).isNotPresent();
    assertThat(activity2Findings.getHigherOutlier()).isPresent();
    assertThat(activity2Findings.getOutlierCount()).isEqualTo(activity2Findings.getHigherOutlier().get().getCount());
    assertThat(activity2Findings.getHigherOutlierHeat()).isGreaterThan(0.0D);
    assertThat(activity2Findings.getLowerOutlierHeat()).isEqualTo(0.0D);
    // second activity has way more higher outliers and thus heat than activity 1
    assertThat(activity2Findings.getHeat()).isGreaterThan(activity1Findings.getHeat());
    assertThat(activity2Findings.getHigherOutlierHeat()).isGreaterThan(activity1Findings.getHigherOutlierHeat());
    assertThat(activity2Findings.getLowerOutlierHeat()).isLessThan(activity1Findings.getLowerOutlierHeat());
  }

  @Test
  public void noOutliersFoundForFlowNodeTest() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    // high sigma value, so that no process instances are out of 2*sigma bounds
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2., 15),
      NUMBER_OF_DATAPOINTS,
      FLOW_NODE_ID_TEST
    );
    // a single higher outlier instance on start and end event but not the FLOW_NODE_ID_TEST activity
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), START_EVENT_ID, 100_000);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), FLOW_NODE_ID_TEST, NUMBER_OF_DATAPOINTS / 2L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), END_EVENT_ID, 100_000);

    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY, Collections.singletonList("1"), Collections.singletonList(null)
    );

    // then
    assertThat(outlierTest)
      .isEmpty();
      // FLOW_NODE_ID_TEST is not part of the result as there are no outliers and start and end event are not
      // computed in the outlier analysis
  }

  @Test
  public void singleOutlierFoundTest() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    // a couple of normally distributed instances
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, FLOW_NODE_ID_TEST
    );
    // a single higher outlier instance
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), FLOW_NODE_ID_TEST, 100_000);

    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null)
    );

    // then
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getOutlierCount()).isEqualTo(1L);
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getLowerOutlierHeat()).isEqualTo(0.0D);
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getHigherOutlierHeat()).isGreaterThan(0.0D);
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getLowerOutlier()).isNotPresent();
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getHigherOutlier()).isPresent();
    assertThat(outlierTest.get(FLOW_NODE_ID_TEST).getHigherOutlier().get().getCount()).isEqualTo(1L);
  }

  @Test
  public void outlierAnalysisRespectsBucketLimit() {
    // given the bucket limit is set to something smaller than the amount of flow nodes present
    final int bucketLimit = 1;
    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(bucketLimit);

    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST,
                                                                                           ANOTHER_FLOW_NODE_ID_TEST));

    // a couple of normally distributed instances
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, FLOW_NODE_ID_TEST
    );
    // a single all flow nodes higher outlier instance
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), 100_000);

    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null)
    );

    // then only one bucket will be there
    assertThat(outlierTest).hasSize(1);
  }

  @Test
  public void allDurationsSameValueNoOutliers() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null)
    );

    // then
    assertThat(outlierTest).isEmpty();
  }

  @Test
  public void moreThan10Activities() {
    // 10 is the default terms limit of elasticsearch, this ensures the default does not apply

    // given
    final String[] activityIds = IntStream.range(0, 12).mapToObj(i -> FLOW_NODE_ID_TEST + i).toArray(String[]::new);
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getBpmnModelInstance(activityIds)
    );

    // a couple of normally distributed instances
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, activityIds
    );
    // a single all flow nodes higher outlier instance
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), 100_000);

    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null)
    );

    // then
    // 12 because the start and end event are no longer considered for the outlier analysis, so really only the 12
    // activities that I created above
    assertThat(outlierTest.size()).isEqualTo(12);
  }

  @Test
  public void durationChartNormalDistributionTest() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS_FOR_CHART / 2., 15),
      NUMBER_OF_DATAPOINTS_FOR_CHART,
      FLOW_NODE_ID_TEST
    );

    importAllEngineEntitiesFromScratch();

    // when
    List<DurationChartEntryDto> durationChart = analysisClient.getDurationChart(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null),
      FLOW_NODE_ID_TEST
    );

    // then
    for (int i = 0; i < durationChart.size() / 2; i++) {
      assertThat(durationChart.get(i).getValue() <= durationChart.get(i + 1).getValue()).isTrue();
    }
    for (int i = durationChart.size() / 2; i < durationChart.size(); i++) {
      assertThat(durationChart.get(i - 1).getValue() >= durationChart.get(i).getValue()).isTrue();
    }
  }

  @Test
  public void durationChartNormalDistributionWithOutlierMarkingTest() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS / 2., 12),
      NUMBER_OF_DATAPOINTS,
      FLOW_NODE_ID_TEST
    );
    final long lowerOutlierBound = 513L;
    final long higherOutlierBound = 39486L;

    importAllEngineEntitiesFromScratch();

    // when
    List<DurationChartEntryDto> durationChart = analysisClient.getDurationChart(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null),
      FLOW_NODE_ID_TEST,
      lowerOutlierBound,
      higherOutlierBound
    );

    // then
    for (int i = 0; i < durationChart.size() / 2; i++) {
      assertThat(durationChart.get(i).isOutlier()).isEqualTo(durationChart.get(i).getKey() < lowerOutlierBound);
    }
    for (int i = durationChart.size() / 2; i < durationChart.size(); i++) {
      assertThat(durationChart.get(i).isOutlier()).isEqualTo(durationChart.get(i).getKey() > higherOutlierBound);
    }
  }

  @Test
  public void durationChartOnePerBucketTest() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    for (int i = 0; i < 80; i++) {
      ProcessInstanceEngineDto processInstance =
        engineIntegrationExtension.startProcessInstance(processDefinition.getId());
      engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), FLOW_NODE_ID_TEST, i * 1000);
    }

    importAllEngineEntitiesFromScratch();

    // when
    List<DurationChartEntryDto> durationChart = analysisClient.getDurationChart(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null),
      FLOW_NODE_ID_TEST
    );

    // then
    assertThat(durationChart.get(0).getValue()).isEqualTo(1L);
    assertThat(durationChart).allMatch(e -> e.getValue().equals(durationChart.get(0).getValue()));
  }

  @Test
  public void significantOutlierVariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));
    // a couple of normally distributed instances
    outlierDistributionClient.createNormalDistributionAnd3Outliers(processDefinition, VARIABLE_VALUE_OUTLIER);

    importAllEngineEntitiesFromScratch();

    // when
    List<VariableTermDto> variableTermDtosActivity = analysisClient.getVariableTermDtosActivity(
      SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND,
      processDefinition.getKey(),
      Collections.singletonList("1"),
      Collections.singletonList(null),
      FLOW_NODE_ID_TEST,
      null
    );

    // then
    assertThat(variableTermDtosActivity).hasSize(1);
    final VariableTermDto variableTermDto = variableTermDtosActivity.get(0);
    assertThat(variableTermDto.getVariableName()).isEqualTo(VARIABLE_2_NAME);
    assertThat(variableTermDto.getVariableTerm()).isEqualTo(VARIABLE_VALUE_OUTLIER);
    assertThat(variableTermDto.getInstanceCount()).isEqualTo(3L);
  }

  @Test
  public void noSignificantOutlierVariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));
    outlierDistributionClient.createNormalDistributionAnd3Outliers(processDefinition, VARIABLE_VALUE_NORMAL);
    // this particular value is obtained from precalculation, given the distribution and outlier setup
    final long activityHigherOutlierBound = SAMPLE_OUTLIERS_HIGHER_OUTLIER_BOUND;

    importAllEngineEntitiesFromScratch();

    // when
    List<VariableTermDto> variableTermDtosActivity = analysisClient.getVariableTermDtosActivity(
      activityHigherOutlierBound,
      processDefinition.getKey(),
      Collections.singletonList("1"),
      Collections.singletonList(null),
      FLOW_NODE_ID_TEST,
      null
    );

    // then
    assertThat(variableTermDtosActivity).isEmpty();
  }

  @Test
  public void noOutliersResultsInNotFoundOnVariables() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition,
      new Gaussian(NUMBER_OF_DATAPOINTS_FOR_CHART / 2., 15),
      NUMBER_OF_DATAPOINTS_FOR_CHART,
      FLOW_NODE_ID_TEST
    );

    // high duration for which there are no instances
    final long activityHigherOutlierBound = 100_000L;

    importAllEngineEntitiesFromScratch();

    // when
    List<VariableTermDto> result = analysisClient.getVariableTermDtosActivity(
      activityHigherOutlierBound,
      processDefinition.getKey(),
      Collections.singletonList("1"),
      Collections.singletonList(null),
      FLOW_NODE_ID_TEST,
      null
    );

    // then
    assertThat(result).isEmpty();
  }

  @Test
  public void significantOutlierVariableValuesProcessInstanceIdExport() {
    // given
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(FLOW_NODE_ID_TEST));

    List<String> expectedOutlierInstanceIds =
      outlierDistributionClient.createNormalDistributionAnd3Outliers(processDefinition, VARIABLE_VALUE_OUTLIER);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String csvContent = getResponseContentAsString(response);
    final String[] lines = csvContent.split("\\r\\n");
    assertThat(lines).hasSize(4);
    assertThat(lines[0]).isEqualTo("\"processInstanceId\"");
    assertThat(ArrayUtils.subarray(lines, 1, 4))
      .containsExactlyInAnyOrder(expectedOutlierInstanceIds.stream().map(s -> "\"" + s + "\"").toArray(String[]::new));
  }

  @Test
  public void standardFilterWorks() {
    // given
    // a model with several task types
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstanceWithDifferentTaskTypes());

    final String[] activityIds = List.of(START_EVENT_ID, SPLITTING_GATEWAY_ID,USER_TASK_ID_ONE,SERVICE_TASK_ID_ONE,MERGING_GATEWAY_ID,
                                         END_EVENT_ID, USER_TASK_ID_TWO,SERVICE_TASK_ID_TWO,MANUAL_TASK_ID).toArray(String[]::new);

    // a couple of normally distributed instances
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, activityIds
    );

    Map<String, Object> variables = new HashMap<>();
    variables.put(SPLITTING_GATEWAY_LABEL, true);
    // a single instance where we turn all flow nodes into outliers
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), 100_000);
    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null)
    );

    // then
    // I expect that all gateways, as well as start and end nodes and also the tasks that are on the side of the
    // gateway that doesn't get executed to not be considered in the outlier analysis
   assertThat(outlierTest.keySet()).containsExactlyInAnyOrder(USER_TASK_ID_ONE, MANUAL_TASK_ID,
                                                                      SERVICE_TASK_ID_ONE);
  }

  @Test
  public void humanTaskFilterWorks() {
    // given
    // a model with several flow types
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstanceWithDifferentTaskTypes());

    final String[] activityIds = List.of(START_EVENT_ID, SPLITTING_GATEWAY_ID,USER_TASK_ID_ONE,SERVICE_TASK_ID_ONE,MERGING_GATEWAY_ID,
                                         END_EVENT_ID, USER_TASK_ID_TWO,SERVICE_TASK_ID_TWO,MANUAL_TASK_ID).toArray(String[]::new);

    // a couple of normally distributed instances
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, activityIds
    );

    Map<String, Object> variables = new HashMap<>();
    variables.put(SPLITTING_GATEWAY_LABEL, true);
    // a single all flow nodes higher outlier instance
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), 100_000);
    importAllEngineEntitiesFromScratch();

    // when
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null),
      0,
      true
    );

    // then
    // I expect that only the relevant user or manual tasks are contained
    assertThat(outlierTest.keySet()).containsExactlyInAnyOrder(USER_TASK_ID_ONE, MANUAL_TASK_ID);
  }
  @Test
  public void minimalDeviationTimeFilterWorks() {
    // given
    // a model with several flow types
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstanceWithDifferentTaskTypes());

    final String[] activityIds = List.of(START_EVENT_ID, SPLITTING_GATEWAY_ID,USER_TASK_ID_ONE,SERVICE_TASK_ID_ONE,MERGING_GATEWAY_ID,
                                         END_EVENT_ID, USER_TASK_ID_TWO,SERVICE_TASK_ID_TWO,MANUAL_TASK_ID).toArray(String[]::new);

    // a couple of normally distributed instances
    outlierDistributionClient.startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, activityIds
    );

    Map<String, Object> variables = new HashMap<>();
    variables.put(SPLITTING_GATEWAY_LABEL, true);
    // a single all flow nodes higher outlier instance
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), 100_000);
    importAllEngineEntitiesFromScratch();

    // when
    // Setting a really high minimum deviation
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList("1"),
      Collections.singletonList(null),
      100_000_000,
      false
    );

    // then
    // I expect all outliers to be ignored
    assertThat(outlierTest).isEmpty();
  }


  private BpmnModelInstance getBpmnModelInstance(String... activityId) {
    StartEventBuilder builder = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .name("aProcessName")
      .startEvent(START_EVENT_ID);
    for (String activity : activityId) {
      builder.serviceTask(activity)
        .camundaExpression("${true}");
    }
    return builder.endEvent(END_EVENT_ID).done();
  }

  @SneakyThrows
  protected static BpmnModelInstance getBpmnModelInstanceWithDifferentTaskTypes() {
    return Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .name(SPLITTING_GATEWAY_LABEL)
      .condition("yes", "${" + SPLITTING_GATEWAY_LABEL + "}")
      .userTask(USER_TASK_ID_ONE)
      .name(USER_TASK_ID_ONE)
      .manualTask(MANUAL_TASK_ID)
      .serviceTask(SERVICE_TASK_ID_ONE)
      .camundaExpression("${true}")
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .endEvent(END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .condition("no", "${" + SPLITTING_GATEWAY_LABEL + "}")
      .userTask(USER_TASK_ID_TWO)
      .name(USER_TASK_ID_TWO)
      .serviceTask(SERVICE_TASK_ID_TWO)
      .camundaExpression("${true}")
      .connectTo(MERGING_GATEWAY_ID)
      .done();
  }

}
