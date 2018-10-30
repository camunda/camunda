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
package io.zeebe.broker.workflow.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.TimerRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BoundaryEventTest {
  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance MULTIPLE_SEQUENCE_FLOWS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(true)
          .timerWithDuration("PT0.1S")
          .endEvent("end1")
          .moveToNode("timer")
          .endEvent("end2")
          .moveToActivity("task")
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldTakeAllOutgoingSequenceFlowsIfTriggered() {
    // given
    testClient.deploy(MULTIPLE_SEQUENCE_FLOWS);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.receiveTimerRecord("timer", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofMinutes(1));
    awaitProcessCompleted();

    // then
    assertThat(testClient.receiveElementInState("end1", WorkflowInstanceIntent.END_EVENT_OCCURRED))
        .isNotNull();
    assertThat(testClient.receiveElementInState("end2", WorkflowInstanceIntent.END_EVENT_OCCURRED))
        .isNotNull();
  }

  @Test
  public void shouldCreateTimerWhenActivityReady() {
    // given
    testClient.deploy(MULTIPLE_SEQUENCE_FLOWS);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    final Record<WorkflowInstanceRecordValue> activityReady =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_READY);
    final Record<TimerRecordValue> timerTriggered =
        testClient.receiveTimerRecord("timer", TimerIntent.CREATE);
    final Record<WorkflowInstanceRecordValue> activityActivated =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then
    assertRecordsPublishedInOrder(activityReady, timerTriggered, activityActivated);
  }

  @Test
  public void shouldTriggerBoundaryEventWhenTimerTriggered() {
    // given
    testClient.deploy(MULTIPLE_SEQUENCE_FLOWS);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.receiveTimerRecord("timer", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofMinutes(1));

    final Record<TimerRecordValue> timerTriggered =
        testClient.receiveTimerRecord("timer", TimerIntent.TRIGGERED);
    final Record<WorkflowInstanceRecordValue> activityTerminating =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final Record<WorkflowInstanceRecordValue> boundaryTriggered =
        testClient.receiveElementInState("timer", WorkflowInstanceIntent.BOUNDARY_EVENT_TRIGGERED);

    awaitProcessCompleted();

    // then
    assertRecordsPublishedInOrder(timerTriggered, activityTerminating, boundaryTriggered);
  }

  private void awaitProcessCompleted() {
    testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  private void assertRecordsPublishedInOrder(final Record<?>... records) {
    final List<Record<?>> sorted =
        Arrays.stream(records)
            .sorted(Comparator.comparingLong(Record::getPosition))
            .collect(Collectors.toList());
    final List<Record<?>> unsorted = Arrays.asList(records);

    assertThat(unsorted).containsExactly(records);
  }
}
