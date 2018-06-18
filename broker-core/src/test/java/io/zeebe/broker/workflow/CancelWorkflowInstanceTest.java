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

import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.*;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ACTIVITY_TERMINATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CANCEL;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {
  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("process")
          .startEvent()
          .serviceTask("task", t -> t.taskType("test").taskRetries(5))
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private TestTopicClient testClient;

  @Before
  public void init() {
    testClient = apiRule.topic();
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    testClient.deploy(WORKFLOW);
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");
    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    final SubscribedRecord cancelWorkflow =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.CANCEL);

    assertThat(response.sourceRecordPosition()).isEqualTo(cancelWorkflow.position());
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELED);

    final SubscribedRecord workflowInstanceCanceledEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CANCELED);

    assertThat(workflowInstanceCanceledEvent.key()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceCanceledEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");

    final List<SubscribedRecord> workflowEvents =
        testClient.receiveRecords().ofTypeWorkflowInstance().limit(9).collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(e -> e.intent())
        .containsExactly(
            WorkflowInstanceIntent.CREATE,
            WorkflowInstanceIntent.CREATED,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ACTIVITY_READY,
            WorkflowInstanceIntent.ACTIVITY_ACTIVATED,
            WorkflowInstanceIntent.CANCEL,
            ACTIVITY_TERMINATED,
            WorkflowInstanceIntent.CANCELED);
  }

  @Test
  public void shouldCancelActivityInstance() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord activityActivatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELED);

    final SubscribedRecord cancelWorkflow =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.CANCEL);
    final SubscribedRecord activityTerminatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(ACTIVITY_TERMINATED);

    assertThat(activityTerminatedEvent.key()).isEqualTo(activityActivatedEvent.key());
    assertThat(activityTerminatedEvent.sourceRecordPosition()).isEqualTo(cancelWorkflow.position());
    assertThat(activityTerminatedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "task");
  }

  @Test
  public void shouldCancelJobForActivity() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final SubscribedRecord jobCreatedEvent =
        testClient.receiveEvents().ofTypeJob().withIntent(JobIntent.CREATED).getFirst();

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELED);

    final SubscribedRecord cancelWorkflow =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.CANCEL);
    final SubscribedRecord jobCancelCmd = testClient.receiveFirstJobCommand(JobIntent.CANCEL);
    final SubscribedRecord jobCanceledEvent = testClient.receiveFirstJobEvent(JobIntent.CANCELED);

    assertThat(jobCanceledEvent.key()).isEqualTo(jobCreatedEvent.key());
    assertThat(jobCancelCmd.sourceRecordPosition()).isEqualTo(cancelWorkflow.position());
    assertThat(jobCanceledEvent.sourceRecordPosition()).isEqualTo(jobCancelCmd.position());

    @SuppressWarnings("unchecked")
    final Map<String, Object> headers =
        (Map<String, Object>) jobCanceledEvent.value().get("headers");
    assertThat(headers)
        .containsEntry("workflowInstanceKey", workflowInstanceKey)
        .containsEntry("bpmnProcessId", "process")
        .containsEntry("workflowDefinitionVersion", 1)
        .containsEntry("activityId", "task");
  }

  @Test
  public void shouldRejectCancelNonExistingWorkflowInstance() {
    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(-1L);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");

    final SubscribedRecord cancelCommand = testClient.receiveFirstWorkflowInstanceCommand(CANCEL);
    final SubscribedRecord cancelRejection =
        testClient
            .receiveRejections()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CANCEL)
            .getFirst();

    assertThat(cancelRejection).isNotNull();
    assertThat(cancelRejection.sourceRecordPosition()).isEqualTo(cancelCommand.position());
  }

  @Test
  public void shouldRejectCancelCompletedWorkflowInstance() {
    // given
    testClient.deploy(Bpmn.createExecutableWorkflow("process").startEvent().endEvent().done());

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    testClient
        .receiveEvents()
        .ofTypeWorkflowInstance()
        .withIntent(WorkflowInstanceIntent.COMPLETED)
        .getFirst();

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");

    final SubscribedRecord cancelCommand = testClient.receiveFirstWorkflowInstanceCommand(CANCEL);
    final SubscribedRecord cancelRejection =
        testClient
            .receiveRejections()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CANCEL)
            .getFirst();

    assertThat(cancelRejection).isNotNull();
    assertThat(cancelRejection.sourceRecordPosition()).isEqualTo(cancelCommand.position());
  }

  private ExecuteCommandResponse cancelWorkflowInstance(final long workflowInstanceKey) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
        .key(workflowInstanceKey)
        .command()
        .done()
        .sendAndAwait();
  }
}
