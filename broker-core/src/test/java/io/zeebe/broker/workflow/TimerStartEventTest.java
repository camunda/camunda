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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.workflow.state.TimerInstance.NO_ELEMENT_INSTANCE;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.EVENT_ACTIVATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.value.DeploymentRecordValue;
import io.zeebe.exporter.record.value.TimerRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TimerStartEventTest {

  public static final BpmnModelInstance SIMPLE_MODEL =
      Bpmn.createExecutableProcess("process")
          .startEvent("start_1")
          .timerWithCycle("R1/PT1S")
          .endEvent("end_1")
          .done();

  public static final BpmnModelInstance REPEATING_MODEL =
      Bpmn.createExecutableProcess("process")
          .startEvent("start_2")
          .timerWithCycle("R/PT1S")
          .endEvent("end_2")
          .done();

  public static final BpmnModelInstance THREE_SEC_MODEL =
      Bpmn.createExecutableProcess("process_3")
          .startEvent("start_3")
          .timerWithCycle("R2/PT3S")
          .endEvent("end_3")
          .done();

  public static BpmnModelInstance multiStartModel;

  public static BpmnModelInstance multiStartSameEndModel;

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  static {
    ProcessBuilder builder = Bpmn.createExecutableProcess("process_4");
    builder.startEvent("start_4").timerWithCycle("R/PT2S").endEvent("end_4");
    multiStartModel =
        builder.startEvent("start_5").timerWithCycle("R/PT3S").endEvent("end_5").done();

    builder = Bpmn.createExecutableProcess("process_5");

    builder.startEvent("start_4").timerWithCycle("R/PT2S").endEvent("end_4");
    multiStartSameEndModel =
        builder.startEvent("start_5").timerWithCycle("R/PT2S").connectTo("end_4").done();
  }

  @Before
  public void setUp() {
    brokerRule.getClock().pinCurrentTime();
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCreateTimer() {
    // when
    testClient.deploy(SIMPLE_MODEL);

    // then
    assertThat(RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).exists()).isTrue();
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED).getFirst().getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(brokerRule.getClock().getCurrentTimeInMillis() + 1000)
        .hasHandlerFlowNodeId("start_1")
        .hasElementInstanceKey(NO_ELEMENT_INSTANCE);
  }

  @Test
  public void shouldTriggerAndCreateWorkflowInstance() {
    // when
    final ExecuteCommandResponse response = testClient.deployWithResponse(SIMPLE_MODEL);
    final DeploymentRecordValue deploymentRecord =
        testClient
            .receiveFirstDeploymentEvent(DeploymentIntent.CREATED, response.getKey())
            .getValue();

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    brokerRule.getClock().addTime(Duration.ofSeconds(2));

    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).exists()).isTrue();
    assertThat(RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.CREATE).exists())
        .isTrue();

    final WorkflowInstanceRecordValue wfInstanceRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
            .getFirst()
            .getValue();

    Assertions.assertThat(wfInstanceRecord)
        .hasVersion(1)
        .hasBpmnProcessId("process")
        .hasWorkflowKey(deploymentRecord.getDeployedWorkflows().get(0).getWorkflowKey());
  }

  @Test
  public void shouldCreateMultipleWorkflowInstancesWithRepeatingTimer() {
    // when
    testClient.deployWithResponse(THREE_SEC_MODEL);

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    brokerRule.getClock().addTime(Duration.ofSeconds(3));

    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).exists()).isTrue();
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
                .withElementId("process_3")
                .exists())
        .isTrue();
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).count()).isEqualTo(2);

    brokerRule.getClock().addTime(Duration.ofSeconds(3));
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).limit(2).count()).isEqualTo(2);
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
                .withElementId("process_3")
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldCompleteWorkflow() {
    // when
    final ExecuteCommandResponse response = testClient.deployWithResponse(SIMPLE_MODEL);
    final DeploymentRecordValue deploymentRecord =
        testClient
            .receiveFirstDeploymentEvent(DeploymentIntent.CREATED, response.getKey())
            .getValue();

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    brokerRule.getClock().addTime(Duration.ofSeconds(1));
    final WorkflowInstanceRecordValue instanceCompleted =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst()
            .getValue();

    Assertions.assertThat(instanceCompleted)
        .hasBpmnProcessId("process")
        .hasVersion(1)
        .hasWorkflowKey(deploymentRecord.getDeployedWorkflows().get(0).getWorkflowKey());
  }

  @Test
  public void shouldUpdateWorkflow() {
    // when
    testClient.deploy(SIMPLE_MODEL);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(EVENT_ACTIVATED)
                .withElementId("end_1")
                .withBpmnProcessId("process")
                .withVersion(1)
                .exists())
        .isTrue();

    // when
    testClient.deploy(REPEATING_MODEL);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).count()).isEqualTo(2);
    brokerRule.getClock().addTime(Duration.ofSeconds(2));

    assertThat(
            RecordingExporter.workflowInstanceRecords(EVENT_ACTIVATED)
                .withElementId("end_2")
                .withBpmnProcessId("process")
                .withVersion(2)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldReplaceTimerStartWithNoneStart() {
    // when
    testClient.deploy(REPEATING_MODEL);

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).exists()).isTrue();

    // when
    final BpmnModelInstance nonTimerModel =
        Bpmn.createExecutableProcess("process").startEvent("start_4").endEvent("end_4").done();
    testClient.deploy(nonTimerModel);

    assertThat(RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).limit(2).count())
        .isEqualTo(2);
    brokerRule.getClock().addTime(Duration.ofSeconds(2));

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CANCELED).exists()).isTrue();
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).exists()).isTrue();

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final WorkflowInstanceRecordValue lastRecord =
        RecordingExporter.workflowInstanceRecords(EVENT_ACTIVATED)
            .withElementId("end_4")
            .getFirst()
            .getValue();

    Assertions.assertThat(lastRecord)
        .hasVersion(2)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldUpdateTimerPeriod() {
    // when
    long beginTime = brokerRule.getClock().getCurrentTimeInMillis();
    testClient.deploy(THREE_SEC_MODEL);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    brokerRule.getClock().addTime(Duration.ofSeconds(3));

    // then
    TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED).getFirst().getValue();

    Assertions.assertThat(timerRecord).hasDueDate(beginTime + 3000);

    // when
    beginTime = brokerRule.getClock().getCurrentTimeInMillis();
    final BpmnModelInstance slowerModel =
        Bpmn.createExecutableProcess("process_3")
            .startEvent("start_4")
            .timerWithCycle("R2/PT4S")
            .endEvent("end_4")
            .done();
    testClient.deploy(slowerModel);

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CANCELED).getFirst()).isNotNull();

    timerRecord = RecordingExporter.timerRecords(TimerIntent.CREATED).skip(2).getFirst().getValue();
    Assertions.assertThat(timerRecord).hasDueDate(beginTime + 4000);
    brokerRule.getClock().addTime(Duration.ofSeconds(3));
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).limit(1).count()).isEqualTo(1);

    brokerRule.getClock().addTime(Duration.ofSeconds(1));
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).limit(2).count()).isEqualTo(2);
  }

  @Test
  public void shouldTriggerDifferentWorkflowsSeparately() {
    // when
    testClient.deploy(THREE_SEC_MODEL);
    testClient.deploy(REPEATING_MODEL);

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).count()).isEqualTo(2);
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
                .withElementId("process")
                .exists())
        .isTrue();
    final Instant firstModelTimestamp =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
            .withElementId("process")
            .getFirst()
            .getTimestamp();

    brokerRule.getClock().addTime(Duration.ofSeconds(2));
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
                .withElementId("process")
                .limit(2)
                .count())
        .isEqualTo(2);
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
                .withElementId("process_3")
                .exists())
        .isTrue();

    final Instant secondModelTimestamp =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_READY)
            .withElementId("process_3")
            .getFirst()
            .getTimestamp();

    assertThat(secondModelTimestamp.isAfter(firstModelTimestamp)).isTrue();
  }

  @Test
  public void shouldCreateMultipleInstanceAtTheCorrectTimes() {
    // when
    testClient.deploy(multiStartModel);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).count()).isEqualTo(2);
    brokerRule.getClock().addTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
                .withElementId("start_4")
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.workflowInstanceRecords(EVENT_ACTIVATED)
                .withElementId("end_4")
                .exists())
        .isTrue();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
                .withElementId("start_5")
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.workflowInstanceRecords(EVENT_ACTIVATED)
                .withElementId("end_5")
                .exists())
        .isTrue();
  }

  public void shouldTriggerAtSpecifiedTimeDate() {
    // given
    final Instant triggerTime = brokerRule.getClock().getCurrentTime().plusMillis(2000);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_2")
            .timerWithDate(triggerTime.toString())
            .endEvent("end_2")
            .done();

    testClient.deploy(model);

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(2));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED).getFirst().getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasHandlerFlowNodeId("start_2")
        .hasElementInstanceKey(NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.workflowInstanceRecords(EVENT_ACTIVATED)
                .withElementId("end_2")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerIfTimeDatePassedOnDeployment() {
    // given
    final Instant triggerTime = brokerRule.getClock().getCurrentTime().minusMillis(2000);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_2")
            .timerWithDate(triggerTime.toString())
            .endEvent("end_2")
            .done();

    testClient.deploy(model);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED).getFirst().getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasHandlerFlowNodeId("start_2")
        .hasElementInstanceKey(NO_ELEMENT_INSTANCE);
  }
}
