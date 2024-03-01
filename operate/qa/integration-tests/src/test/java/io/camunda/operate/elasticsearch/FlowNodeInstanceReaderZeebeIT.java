/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class FlowNodeInstanceReaderZeebeIT extends OperateZeebeAbstractIT {

  @Test
  public void testFlowNodeStatisticsForASimpleProcessInstance() throws Exception {
    // given
    final Long processInstanceKey =
        tester
            .deployProcess("single-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .startProcessInstance("process")
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .flowNodeIsActive("task")
            .getProcessInstanceKey();

    List<FlowNodeStatisticsDto> flowNodeStatistics =
        getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertThat(flowNodeStatistics.size()).as("FlowNodeStatistics size should be 2").isEqualTo(2);
    assertStatistic(flowNodeStatistics, "StartEvent_1", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "task", 0, 1, 0, 0);

    // when
    tester.activateFlowNode("task").waitUntil().flowNodesAreActive("task", 2);
    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then
    assertThat(flowNodeStatistics.size()).as("FlowNodeStatistics size should be 2").isEqualTo(2);
    assertStatistic(flowNodeStatistics, "StartEvent_1", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "task", 0, 2, 0, 0);
    // and when
    tester.cancelAllFlowNodesFor("task").waitUntil().flowNodesAreCanceled("task", 2);

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
    BpmnModelInstance modelInstance = flowNodeBuilder.endEvent(endEvent).done();
    // complete all tasks and the whole instance
    tester
        .deployProcess(modelInstance, "20task.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("process");
    for (int i = 0; i < 20; i++) {
      tester.completeTask("task" + i, jobType);
    }
    final Long processInstanceKey =
        tester.waitUntil().processInstanceIsCompleted().getProcessInstanceKey();

    // when
    List<FlowNodeStatisticsDto> flowNodeStatistics =
        getFlowNodeStatisticsForProcessInstance(processInstanceKey);

    // then
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
    final Long processInstanceKey =
        tester
            .deployProcess("complexProcess_v_3.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .startProcessInstance("complexProcess")
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .waitUntil()
            .flowNodeIsCompleted("startEvent")
            // no input var is provided, upperTask will fail
            .and()
            .flowNodeIsActive("upperTask")
            .incidentsInAnyInstanceAreActive(3)
            .and()
            .flowNodeIsActive("alwaysFailingTask")
            .then()
            .getProcessInstanceKey();

    List<FlowNodeStatisticsDto> flowNodeStatistics =
        getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 6").isEqualTo(6);
    assertStatistic(flowNodeStatistics, "startEvent", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "upperTask", 0, 0, 0, 1);
    assertStatistic(flowNodeStatistics, "alwaysFailingTask", 0, 1, 0, 0);

    // when
    tester
        .cancelAllFlowNodesFor("alwaysFailingTask")
        .waitUntil()
        .flowNodesAreTerminated("alwaysFailingTask", 1);

    flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    // then
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 6").isEqualTo(6);
    assertStatistic(flowNodeStatistics, "startEvent", 1, 0, 0, 0);
    assertStatistic(flowNodeStatistics, "upperTask", 0, 0, 0, 1);
    assertStatistic(flowNodeStatistics, "alwaysFailingTask", 0, 0, 1, 0);
    // and when
    tester
        .activateFlowNode("alwaysFailingTask")
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
    final Long processInstanceKey =
        tester
            .deployProcess("subProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .startProcessInstance("prWithSubprocess")
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .waitUntil()
            .flowNodeIsActive("taskA")
            .then()
            .getProcessInstanceKey();

    // when
    List<FlowNodeStatisticsDto> flowNodeStatistics =
        getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertThat(flowNodeStatistics.size()).as("flowNodeStatistics size should be 2").isEqualTo(2);

    // and when
    tester.completeTask("taskA").and().waitUntil().incidentIsActive();

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
    var processInstanceKey =
        tester
            .deployProcess("develop/multi-instance-service-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .startProcessInstance("multiInstanceServiceProcess", tester.getItemsPayloadFor(3))
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .flowNodesAreActive("serviceTask", 3)
            .then()
            .getProcessInstanceKey();

    var flowNodeStatistics = getFlowNodeStatisticsForProcessInstance(processInstanceKey);
    assertStatisticActiveOrIncident(flowNodeStatistics, "serviceTask", 0, 3, 0);
  }

  @Test // due to https://github.com/camunda/operate/issues/3362
  public void testMultiInstanceCountWithIncidentInBody() throws Exception {
    // given
    var wrongPayload = "{\"items\": \"\"}";
    // when
    tester
        .deployProcess("develop/multi-instance-service-task.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("multiInstanceServiceProcess", wrongPayload)
        .waitUntil()
        .processInstanceIsStarted()
        .then()
        .waitUntil()
        .incidentIsActive();

    // then
    var flowNodeStatistics =
        getFlowNodeStatisticsForProcessInstance(tester.getProcessInstanceKey());
    assertStatistic(flowNodeStatistics, "serviceTask", 0, 0, 0, 1);
  }

  @Test
  public void testFlowNodeStatisticsForASequentialMultiInstance() throws Exception {
    // given
    final Long processInstanceKey =
        tester
            .deployProcess("sequential-noop.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .startProcessInstance("sequential-noop", tester.getItemsPayloadFor(3))
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .waitUntil()
            .flowNodesAreCompleted("subprocess-end-event", 3)
            .and()
            .waitUntil()
            .flowNodeIsCompleted("end-event")
            .getProcessInstanceKey();

    // when
    List<FlowNodeStatisticsDto> flowNodeStatistics =
        getFlowNodeStatisticsForProcessInstance(processInstanceKey);
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

  private List<FlowNodeStatisticsDto> getFlowNodeStatisticsForProcessInstance(
      final Long processInstanceKey) throws Exception {
    searchTestRule.refreshSerchIndexes();
    return mockMvcTestRule.listFromResponse(
        getRequest("/api/process-instances/" + processInstanceKey + "/statistics"),
        FlowNodeStatisticsDto.class);
  }

  private void assertStatistic(
      final List<FlowNodeStatisticsDto> flowNodeStatistics,
      final String flowNodeId,
      final long completed,
      final long active,
      final long canceled,
      final long incident) {
    final Optional<FlowNodeStatisticsDto> flowNodeStatistic =
        flowNodeStatistics.stream()
            .filter(fns -> fns.getActivityId().equals(flowNodeId))
            .findFirst();
    if (flowNodeStatistic.isPresent()) {
      final FlowNodeStatisticsDto flowNodeStatisticsDto = flowNodeStatistic.get();
      assertThat(flowNodeStatisticsDto.getCompleted())
          .as("completed for %s should be %d", flowNodeId, completed)
          .isEqualTo(completed);
      assertThat(flowNodeStatisticsDto.getCanceled())
          .as("canceled for %s should be %d", flowNodeId, canceled)
          .isEqualTo(canceled);
      assertThat(flowNodeStatisticsDto.getActive())
          .as("active for %s should be %d", flowNodeId, active)
          .isEqualTo(active);
      assertThat(flowNodeStatisticsDto.getIncidents())
          .as("incidents for %s should be %d", flowNodeId, incident)
          .isEqualTo(incident);
    } else {
      fail("No flowNodeStatistic found for " + flowNodeId);
    }
  }

  private void assertStatisticActiveOrIncident(
      final List<FlowNodeStatisticsDto> flowNodeStatistics,
      final String flowNodeId,
      final long completed,
      final long active,
      final long canceled) {
    final Optional<FlowNodeStatisticsDto> flowNodeStatistic =
        flowNodeStatistics.stream()
            .filter(fns -> fns.getActivityId().equals(flowNodeId))
            .findFirst();
    if (flowNodeStatistic.isPresent()) {
      final FlowNodeStatisticsDto flowNodeStatisticsDto = flowNodeStatistic.get();
      assertThat(flowNodeStatisticsDto.getCompleted())
          .as("completed for %s should be %d", flowNodeId, completed)
          .isEqualTo(completed);
      assertThat(flowNodeStatisticsDto.getCanceled())
          .as("canceled for %s should be %d", flowNodeId, canceled)
          .isEqualTo(canceled);
      assertThat(flowNodeStatisticsDto.getActive() + flowNodeStatisticsDto.getIncidents())
          .as("active+incidents for %s should be %d", flowNodeId, active)
          .isEqualTo(active);
    } else {
      fail("No flowNodeStatistic found for " + flowNodeId);
    }
  }
}
