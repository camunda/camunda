/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.engine.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.WorkflowInstances;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ActivityTest {
  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WITHOUT_BOUNDARY_EVENTS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              "task",
              b -> b.zeebeTaskType("type").zeebeInput("foo", "bar").zeebeOutput("bar", "oof"))
          .endEvent()
          .done();

  private static final BpmnModelInstance WITH_BOUNDARY_EVENTS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer1")
          .timerWithDuration("PT10S")
          .endEvent()
          .moveToActivity("task")
          .boundaryEvent("timer2")
          .timerWithDuration("PT20S")
          .endEvent()
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);
  private PartitionTestClient testClient;

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldApplyInputMappingOnReady() {
    // given
    testClient.deploy(WITHOUT_BOUNDARY_EVENTS);
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(PROCESS_ID)
                        .setVariables(MsgPackUtil.asMsgPack("{ \"foo\": 1, \"boo\": 2 }")))
            .getInstanceKey();

    // when
    final Record<WorkflowInstanceRecordValue> record =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, record.getPosition());
    assertThat(variables).contains(entry("bar", "1"));
  }

  @Test
  public void shouldApplyOutputMappingOnCompleting() {
    // given
    testClient.deploy(WITHOUT_BOUNDARY_EVENTS);
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(PROCESS_ID)
                        .setVariables(MsgPackUtil.asMsgPack("{ \"foo\": 1, \"boo\": 2 }")))
            .getInstanceKey();

    // when
    final Record<JobRecordValue> jobRecord = testClient.receiveFirstJobEvent(JobIntent.CREATED);
    testClient.completeJob(jobRecord.getKey(), jobRecord.getValue().getVariables());

    // then
    final Record<WorkflowInstanceRecordValue> record =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, record.getPosition());
    assertThat(variables).contains(entry("bar", "1"));
  }

  @Test
  public void shouldSubscribeToBoundaryEventTriggersOnReady() {
    // given
    testClient.deploy(WITH_BOUNDARY_EVENTS);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    final Record<WorkflowInstanceRecordValue> readyRecord =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record<WorkflowInstanceRecordValue> activatedRecord =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final List<Record<TimerRecordValue>> subscriptions =
        Arrays.asList(
            testClient.receiveTimerRecord("timer1", TimerIntent.CREATE),
            testClient.receiveTimerRecord("timer2", TimerIntent.CREATE));

    // then
    assertThat(subscriptions).hasSize(2);
    for (final Record<TimerRecordValue> subscription : subscriptions) {
      assertThat(subscription.getPosition())
          .isBetween(readyRecord.getPosition(), activatedRecord.getPosition());
      assertThat(subscription.getValue().getElementInstanceKey()).isEqualTo(readyRecord.getKey());
    }
  }

  @Test
  public void shouldUnsubscribeFromBoundaryEventTriggersOnCompleting() {
    // given
    testClient.deploy(WITH_BOUNDARY_EVENTS);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    final Record<JobRecordValue> job = testClient.receiveFirstJobEvent(JobIntent.CREATED);
    testClient.completeJob(job.getKey(), job.getValue().getVariables());
    testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // then
    shouldUnsubscribeFromBoundaryEventTrigger(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldUnsubscribeFromBoundaryEventTriggersOnTerminating() {
    // given
    testClient.deploy(WITH_BOUNDARY_EVENTS);
    final long workflowKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    // when
    testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    testClient.cancelWorkflowInstance(workflowKey);
    testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    // then
    shouldUnsubscribeFromBoundaryEventTrigger(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, WorkflowInstanceIntent.ELEMENT_TERMINATED);
  }

  @Test
  public void shouldIgnoreTaskHeadersIfEmpty() {
    createWorkflowAndAssertIgnoredHeaders("");
  }

  @Test
  public void shouldIgnoreTaskHeadersIfNull() {
    createWorkflowAndAssertIgnoredHeaders(null);
  }

  private void createWorkflowAndAssertIgnoredHeaders(String testValue) {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task1", b -> b.zeebeTaskType("type1").zeebeTaskHeader("key", testValue))
            .endEvent("end")
            .moveToActivity("task1")
            .serviceTask("task2", b -> b.zeebeTaskType("type2").zeebeTaskHeader(testValue, "value"))
            .connectTo("end")
            .moveToActivity("task1")
            .serviceTask(
                "task3", b -> b.zeebeTaskType("type3").zeebeTaskHeader(testValue, testValue))
            .connectTo("end")
            .done();

    // when
    final long deploymentKey = testClient.deploy(model);
    testClient.receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process"));

    // then
    final JobRecordValue firstJob =
        testClient.receiveJobs().withType("type1").getFirst().getValue();
    assertThat(firstJob.getCustomHeaders()).isEmpty();
    testClient.completeJobOfType("type1");

    final JobRecordValue secondJob =
        testClient.receiveJobs().withType("type2").getFirst().getValue();
    assertThat(secondJob.getCustomHeaders()).isEmpty();
    testClient.completeJobOfType("type2");

    final JobRecordValue thirdJob =
        testClient.receiveJobs().withType("type3").getFirst().getValue();
    assertThat(thirdJob.getCustomHeaders()).isEmpty();
  }

  private void shouldUnsubscribeFromBoundaryEventTrigger(
      WorkflowInstanceIntent leavingState, WorkflowInstanceIntent leftState) {
    // given
    final Record<WorkflowInstanceRecordValue> leavingRecord =
        testClient.receiveElementInState("task", leavingState);
    final Record<WorkflowInstanceRecordValue> leftRecord =
        testClient.receiveElementInState("task", leftState);
    final List<Record<TimerRecordValue>> subscriptions =
        Arrays.asList(
            testClient.receiveTimerRecord("timer1", TimerIntent.CANCEL),
            testClient.receiveTimerRecord("timer2", TimerIntent.CANCEL));

    // then
    assertThat(subscriptions).hasSize(2);
    for (final Record<TimerRecordValue> subscription : subscriptions) {
      assertThat(subscription.getPosition())
          .isBetween(leavingRecord.getPosition(), leftRecord.getPosition());
    }
  }
}
