/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public class FlowNodeInstanceReaderIT extends OperateZeebeIntegrationTest {

  @Test
  public void testFlowNodeStatisticsForASimpleProcessInstance() throws Exception {
    // given
    final Long processInstanceKey = tester.deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("process")
        .waitUntil()
          .processInstanceIsStarted()
          .and().flowNodeIsActive("task")
        .getProcessInstanceKey();

    List<FlowNodeStatisticsDto> flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertThat(flowNodeStatistics.size()).as("FlowNodeStatistics size should be 2").isEqualTo(2);
    assertStatistic(flowNodeStatistics, "StartEvent_1", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "task", 0, 1, 0, 0);

    // when
    tester.activateFlowNode("task")
        .waitUntil()
        .flowNodesAreActive("task", 2);
    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then
    assertThat(flowNodeStatistics.size()).as("FlowNodeStatistics size should be 2").isEqualTo(2);
    assertStatistic(flowNodeStatistics, "StartEvent_1", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "task", 0, 2, 0, 0);
    // and when
    tester.cancelAllFlowNodesFor("task")
        .waitUntil()
        .flowNodesAreCanceled("task", 2);

    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then
    assertThat(flowNodeStatistics.size()).as("FlowNodeStatistics size should be 2").isEqualTo(2);
    assertStatistic(flowNodeStatistics, "StartEvent_1", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "task", 0, 0, 2, 0);
  }

  @Test
  public void testFlowNodeStatisticsForManyFlowNodes() throws Exception {
    // given
    String startEvent = "start";
    String endEvent = "end";
    String jobType = "taskA";
    ProcessBuilder processBuilder = Bpmn.createExecutableProcess("process");
    AbstractFlowNodeBuilder flowNodeBuilder = processBuilder.startEvent(startEvent);
    for (int i = 0; i < 20; i++) {
      flowNodeBuilder = flowNodeBuilder.serviceTask("task" + i).zeebeJobType(jobType);
    }
    BpmnModelInstance modelInstance = flowNodeBuilder
        .endEvent(endEvent)
        .done();
    //complete all tasks and the whole instance
    tester.deployProcess(modelInstance, "20task.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("process");
    for (int i = 0; i < 20; i++) {
      tester.completeTask("task" + i, jobType);
    }
    final Long processInstanceKey = tester.waitUntil()
        .processInstanceIsCompleted()
        .getProcessInstanceKey();

    //when
    List<FlowNodeStatisticsDto> flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);

    //then
    assertThat(flowNodeStatistics.size()).as("FlowNodeStatistics size should be 22").isEqualTo(22);
    assertStatistic(flowNodeStatistics, startEvent, 1, 0, 0, 0);
    for (int i = 0; i < 20; i++) {
      assertStatistic(flowNodeStatistics, "task" + i, 1, 0, 0, 0);
    }
    assertStatistic(flowNodeStatistics, endEvent, 1, 0, 0, 0);

  }

  @Test
  public void testFlowNodeStatisticsForAComplexProcessInstance() throws Exception {
    // given
    final Long processInstanceKey = tester.deployProcess("complexProcess_v_3.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("complexProcess")
        .waitUntil().processInstanceIsStarted()
        .and().waitUntil()
          .flowNodeIsCompleted("startEvent")
        //no input var is provided, upperTask will fail
          .and().flowNodeIsActive("upperTask").incidentsInAnyInstanceAreActive(3)
          .and().flowNodeIsActive("alwaysFailingTask")
        .then()
        .getProcessInstanceKey();

    List<FlowNodeStatisticsDto> flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 6").isEqualTo(6);
    assertStatistic(flowNodeStatistics, "startEvent", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "upperTask", 0, 0, 0, 1);
    assertStatistic(flowNodeStatistics, "alwaysFailingTask", 0, 1, 0, 0);

    // when
    tester.cancelAllFlowNodesFor("alwaysFailingTask")
        .waitUntil()
        .flowNodesAreTerminated("alwaysFailingTask", 1);

    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 6").isEqualTo(6);
    assertStatistic(flowNodeStatistics, "startEvent", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "upperTask", 0, 0, 0, 1);
    assertStatistic(flowNodeStatistics, "alwaysFailingTask", 0, 0, 1, 0);
    // and when
    tester.activateFlowNode("alwaysFailingTask")
        .and()
        .activateFlowNode("alwaysFailingTask")
        .waitUntil()
        .flowNodesExist("alwaysFailingTask", 3);
    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 6").isEqualTo(6);
    assertStatistic(flowNodeStatistics, "startEvent", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "upperTask", 0, 0, 0, 1);
    assertStatistic(flowNodeStatistics, "alwaysFailingTask", 0, 2, 1, 0);
  }

  @Test
  public void testFlowNodeStatisticsForSubProcess() throws Exception {
    // given
    final Long processInstanceKey = tester.deployProcess("subProcess.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("prWithSubprocess")
        .waitUntil().processInstanceIsStarted()
        .and().waitUntil()
        .flowNodeIsActive("taskA")
        .then()
        .getProcessInstanceKey();

    // when
    List<FlowNodeStatisticsDto> flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 2").isEqualTo(2);

    // and when
    tester.completeTask("taskA")
        .and().waitUntil()
        .incidentIsActive();

    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then now subprocesses or multi instances should be there
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 7").isEqualTo(7);
    assertStatistic(flowNodeStatistics, "startEvent", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "taskA", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "subprocess", 0, 1, 0, 0);
    assertStatistic(flowNodeStatistics, "startEventSubprocess", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "innerSubprocess", 0, 1, 0, 0);
    assertStatistic(flowNodeStatistics, "startEventInnerSubprocess", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "taskB", 0, 0, 0, 1);
  }

  @Test // due to https://github.com/camunda/operate/issues/3362
  public void testMultiInstanceActiveCount() throws Exception {
      var processInstanceKey = tester.deployProcess("develop/multi-instance-service-task.bpmn")
          .waitUntil().processIsDeployed()
          .then().startProcessInstance("multiInstanceServiceProcess", tester.getItemsPayloadFor(3))
          .waitUntil().processInstanceIsStarted()
          .and().flowNodesAreActive("serviceTask", 3)
          .then().getProcessInstanceKey();

      var flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
      assertStatisticActiveOrIncident(flowNodeStatistics, "serviceTask", 0, 3, 0);
  }

  @Test // due to https://github.com/camunda/operate/issues/3362
  public void testMultiInstanceCountWithIncidentInBody() throws Exception {
    // given
    var  wrongPayload = "{\"items\": \"\"}";
    // when
    tester.deployProcess("develop/multi-instance-service-task.bpmn")
        .waitUntil().processIsDeployed()
        .then().startProcessInstance("multiInstanceServiceProcess", wrongPayload)
        .waitUntil().processInstanceIsStarted()
        .then().waitUntil().incidentIsActive();

    // then
    var flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(
        tester.getProcessInstanceKey());
    assertStatistic(flowNodeStatistics, "serviceTask", 0, 0, 0, 1);
  }

  @Test
  public void testFlowNodeStatisticsForASequentialMultiInstance() throws Exception {
    // given
    final Long processInstanceKey = tester.deployProcess("sequential-noop.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("sequential-noop", tester.getItemsPayloadFor(3))
        .waitUntil().processInstanceIsStarted()
        .and().waitUntil()
        .flowNodesAreCompleted("subprocess-end-event", 3)
        .and().waitUntil()
        .flowNodeIsCompleted("end-event")
        .getProcessInstanceKey();

    // when
    List<FlowNodeStatisticsDto> flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then statistics without sequential sub process
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 7").isEqualTo(7);
    assertStatistic(flowNodeStatistics, "start-event", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "sequential-sub-process", 3, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "fork", 3, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "join", 3, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "subprcess-start-event", 3, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "subprocess-end-event", 3, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "end-event", 1, 0, 0, 0);
  }

  private List<FlowNodeStatisticsDto> getFlowNodeStatisticsForProcessInstance(final Long processInstanceKey)
      throws Exception {
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mockMvcTestRule.listFromResponse(
        getRequest("/api/process-instances/"+processInstanceKey+"/statistics"), FlowNodeStatisticsDto.class);
  }

  private void assertStatistic(final List<FlowNodeStatisticsDto> flowNodeStatistics,
      final String flowNodeId,
      final long completed, final long active,final long canceled, final long incident){
    final Optional<FlowNodeStatisticsDto> flowNodeStatistic = flowNodeStatistics.stream().filter(
        fns -> fns.getActivityId().equals(flowNodeId)).findFirst();
    if(flowNodeStatistic.isPresent()){
      final FlowNodeStatisticsDto flowNodeStatisticsDto = flowNodeStatistic.get();
      assertThat(flowNodeStatisticsDto.getCompleted())
          .as("completed for %s should be %d", flowNodeId, completed).isEqualTo(completed);
      assertThat(flowNodeStatisticsDto.getCanceled())
          .as("canceled for %s should be %d", flowNodeId, canceled).isEqualTo(canceled);
      assertThat(flowNodeStatisticsDto.getActive())
          .as("active for %s should be %d", flowNodeId, active).isEqualTo(active);
      assertThat(flowNodeStatisticsDto.getIncidents())
          .as("incidents for %s should be %d", flowNodeId, incident).isEqualTo(incident);
    }else{
      fail("No flowNodeStatistic found for " + flowNodeId);
    }
  }

  private void assertStatisticActiveOrIncident(final List<FlowNodeStatisticsDto> flowNodeStatistics,
    final String flowNodeId,
    final long completed, final long active,final long canceled){
      final Optional<FlowNodeStatisticsDto> flowNodeStatistic = flowNodeStatistics.stream().filter(
          fns -> fns.getActivityId().equals(flowNodeId)).findFirst();
      if(flowNodeStatistic.isPresent()){
        final FlowNodeStatisticsDto flowNodeStatisticsDto = flowNodeStatistic.get();
        assertThat(flowNodeStatisticsDto.getCompleted())
            .as("completed for %s should be %d", flowNodeId, completed).isEqualTo(completed);
        assertThat(flowNodeStatisticsDto.getCanceled())
            .as("canceled for %s should be %d", flowNodeId, canceled).isEqualTo(canceled);
        assertThat(flowNodeStatisticsDto.getActive() + flowNodeStatisticsDto.getIncidents())
            .as("active+incidents for %s should be %d", flowNodeId, active).isEqualTo(active);
      }else{
        fail("No flowNodeStatistic found for " + flowNodeId);
      }
  }
}
