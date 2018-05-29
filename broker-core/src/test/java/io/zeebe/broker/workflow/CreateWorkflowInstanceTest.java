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

import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_PAYLOAD;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_KEY;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_PAYLOAD;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import io.zeebe.util.StreamUtil;


public class CreateWorkflowInstanceTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();
    private TestTopicClient testClient;

    @Before
    public void init()
    {
        testClient = apiRule.topic();
    }

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldRejectWorkflowInstanceCreation()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
        assertThat(resp.rejectionReason()).isEqualTo("Workflow is not deployed");
        assertThat(resp.getValue())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");

    }

    @Test
    public void shouldCreateWorkflowInstanceByBpmnProcessId()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);
        assertThat(resp.getValue())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
    }

    @Test
    public void shouldCreateWorkflowInstanceByBpmnProcessIdAndLatestVersion()
    {
        // given
        testClient.deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
            Bpmn.createExecutableWorkflow("process")
                .startEvent("bar")
                .endEvent()
                .done());

        final ExecuteCommandResponse deployment2 = testClient.deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
            Bpmn.createExecutableWorkflow("process")
                .startEvent("bar")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_VERSION, -1)
                .done()
                .sendAndAwait();

        // then
        final int workflowKey = extractWorkflowKey(deployment2);

        final SubscribedRecord event = testClient.receiveEvents()
                .ofTypeWorkflowInstance()
                .withIntent(WorkflowInstanceIntent.START_EVENT_OCCURRED)
                .getFirst();

        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key())
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "bar")
            .containsEntry(PROP_WORKFLOW_VERSION, 2)
            .containsEntry(PROP_WORKFLOW_KEY, workflowKey);
    }

    @Test
    public void shouldCreateWorkflowInstanceByBpmnProcessIdAndPreviosuVersion()
    {
        // given
        final ExecuteCommandResponse deployment1 = testClient.deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
             Bpmn.createExecutableWorkflow("process")
                 .startEvent("foo")
                 .endEvent()
                 .done());

        testClient.deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
             Bpmn.createExecutableWorkflow("process")
                 .startEvent("bar")
                 .endEvent()
                 .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_VERSION, 1)
                .done()
                .sendAndAwait();

        // then
        final int workflowKey = extractWorkflowKey(deployment1);

        final SubscribedRecord event = testClient.receiveEvents()
                .ofTypeWorkflowInstance()
                .withIntent(WorkflowInstanceIntent.START_EVENT_OCCURRED)
                .getFirst();

        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key())
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_KEY, workflowKey);
    }

    @Test
    public void shouldCreateWorkflowInstanceByWorkflowKeyAndLatestVersion()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
              .startEvent("foo")
              .endEvent()
              .done());

        final ExecuteCommandResponse depl = testClient.deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
            Bpmn.createExecutableWorkflow("process")
                .startEvent("bar")
                .endEvent()
                .done());

        final int workflowKey = extractWorkflowKey(depl);

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_KEY, workflowKey)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);
        assertThat(resp.getValue())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 2)
            .containsEntry(PROP_WORKFLOW_KEY, workflowKey)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
    }

    @Test
    public void shouldCreateWorkflowInstanceByWorkflowKeyAndPreviousVersion()
    {
        // given
        final ExecuteCommandResponse depl = testClient.deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
            Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent()
                .done());

        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                  .startEvent()
                  .endEvent()
                  .done());

        final int workflowKey = extractWorkflowKey(depl);

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_KEY, workflowKey)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);
        assertThat(resp.getValue())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_KEY, workflowKey)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
    }

    @Test
    public void shouldCreateWorkflowInstanceWithPayload()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.intent()).isEqualTo(WorkflowInstanceIntent.CREATED);
        assertThat(resp.getValue())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key())
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD);
    }

    @Test
    public void shouldRejectWorkflowInstanceWithInvalidPayload() throws Exception
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                              .startEvent()
                              .endEvent()
                              .done());

        // when
        final byte[] invalidPayload = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("'foo'"));

        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
                .command()
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_PAYLOAD, invalidPayload)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
        assertThat(resp.rejectionReason()).isEqualTo("Payload is not a valid msgpack-encoded JSON object or nil");
        assertThat(resp.getValue())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
    }

    @Test
    public void shouldCreateMultipleWorkflowInstancesForDifferentBpmnProcessIds()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("foo")
                .startEvent()
                .endEvent()
                .done());

        testClient.deploy(Bpmn.createExecutableWorkflow("baaaar")
                .startEvent()
                .endEvent()
                .done());

        // when
        final long workflowInstanceKeyFoo = testClient.createWorkflowInstance("foo");
        final long workflowInstanceKeyBaaaar = testClient.createWorkflowInstance("baaaar");

        // then
        final List<SubscribedRecord> workflowInstanceEvents = testClient.receiveEvents()
                .ofTypeWorkflowInstance()
                .withIntent(WorkflowInstanceIntent.CREATED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(workflowInstanceEvents.get(0).value())
            .containsEntry("bpmnProcessId", "foo")
            .containsEntry("workflowInstanceKey", workflowInstanceKeyFoo);

        assertThat(workflowInstanceEvents.get(1).value())
            .containsEntry("bpmnProcessId", "baaaar")
            .containsEntry("workflowInstanceKey", workflowInstanceKeyBaaaar);
    }

    @Test
    public void shouldCreateMultipleWorkflowInstancesForDifferentVersionsOnForceRefresh()
    {
        // given
        final WorkflowDefinition workflow = Bpmn.createExecutableWorkflow("process")
             .startEvent("start")
             .serviceTask("task", task -> task
                          .taskType("test")
                          .taskRetries(3)
                          .taskHeader("foo", "bar"))
             .endEvent("end")
             .done();

        testClient.deploy(workflow);

        final long workflowInstance1 = testClient.createWorkflowInstance("process");

        testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
            .getFirst();

        // when
        testClient.deploy(workflow);

        // -2 == force refresh
        final ExecuteCommandResponse resp = testClient.createWorkflowInstanceWithResponse("process", -2);

        // then
        final List<SubscribedRecord> workflowInstanceEvents = testClient.receiveEvents()
                .ofTypeWorkflowInstance()
                .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(workflowInstanceEvents.get(0).value())
            .containsEntry("workflowInstanceKey", workflowInstance1)
            .containsEntry("version", 1);

        assertThat(workflowInstanceEvents.get(1).value())
            .containsEntry("workflowInstanceKey", resp.key())
            .containsEntry("version", 2);

        final long createdTasks = testClient.receiveEvents()
            .ofTypeJob()
            .withIntent(JobIntent.CREATED)
            .limit(2).count();
        assertThat(createdTasks).isEqualTo(2);
    }

    @Test
    public void shouldCreateMultipleWorkflowInstancesForDifferentVersionsAfterTimeout() throws InterruptedException
    {
        // given
        final WorkflowDefinition workflow = Bpmn.createExecutableWorkflow("process")
             .startEvent("start")
             .serviceTask("task", task -> task
                          .taskType("test")
                          .taskRetries(3)
                          .taskHeader("foo", "bar"))
             .endEvent("end")
             .done();

        testClient.deploy(workflow);

        final long workflowInstance1 = testClient.createWorkflowInstance("process");

        testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
            .getFirst();

        // when
        testClient.deploy(workflow);

        // when wait for refresh timeout
        brokerRule.getClock()
            .addTime(Duration.ofSeconds(WorkflowCache.LATEST_VERSION_REFRESH_INTERVAL + 1));

        final ExecuteCommandResponse resp = testClient.createWorkflowInstanceWithResponse("process");

        // then
        final List<SubscribedRecord> workflowInstanceEvents = testClient.receiveEvents()
                .ofTypeWorkflowInstance()
                .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(workflowInstanceEvents.get(0).value())
            .containsEntry("workflowInstanceKey", workflowInstance1)
            .containsEntry("version", 1);

        assertThat(workflowInstanceEvents.get(1).value())
            .containsEntry("workflowInstanceKey", resp.key())
            .containsEntry("version", 2);

        final long createdJobs = testClient.receiveEvents()
                .ofTypeJob()
                .withIntent(JobIntent.CREATED)
                .limit(2).count();
        assertThat(createdJobs).isEqualTo(2);
    }

    @Test
    public void shouldCreateInstanceOfYamlWorkflow() throws Exception
    {
        // given
        final InputStream resourceAsStream = getClass().getResourceAsStream("/workflows/simple-workflow.yaml");

        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    StreamUtil.read(resourceAsStream),
                                    ResourceType.YAML_WORKFLOW.name(),
                                    "simple-workflow.yaml");

        assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATED);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("yaml-workflow");

        // then
        final SubscribedRecord workflowInstanceEvent = testClient.receiveEvents().ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CREATED)
            .getFirst();

        assertThat(workflowInstanceEvent.value())
            .containsEntry("bpmnProcessId", "yaml-workflow")
            .containsEntry("workflowInstanceKey", workflowInstanceKey);
    }

    @Test
    public void shouldCreateWorkflowInstanceOnAllPartitions()
    {
        // given
        final int partitions = 3;

        apiRule.createTopic("test", partitions);
        final List<Integer> partitionIds = apiRule.getPartitionsFromTopology("test");

        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        apiRule.topic().deploy("test", definition);

        // then
        final List<Long> workflowInstanceKeys = new ArrayList<>();
        partitionIds.forEach(partitionId ->
        {
            final long workflowInstanceKey = apiRule.topic(partitionId).createWorkflowInstance("process");

            workflowInstanceKeys.add(workflowInstanceKey);
        });

        assertThat(workflowInstanceKeys)
            .hasSize(partitions)
            .allMatch(k -> k > 0);
    }

    @Test
    public void shouldCreateWorkflowInstanceOfCollaboration() throws IOException
    {
        final InputStream resourceAsStream = getClass().getResourceAsStream("/workflows/collaboration.bpmn");

        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    StreamUtil.read(resourceAsStream),
                                    ResourceType.BPMN_XML.name(),
                                    "collaboration.bpmn");

        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.intent()).isEqualTo(DeploymentIntent.CREATED);

        // when
        final long wfInstance1 = testClient.createWorkflowInstance("process1");
        final long wfInstance2 = testClient.createWorkflowInstance("process2");

        // then
        final SubscribedRecord event1 = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CREATED)
            .filter(r -> r.key() == wfInstance1)
            .findFirst()
            .get();

        assertThat(event1.value().get("bpmnProcessId")).isEqualTo("process1");

        final SubscribedRecord event2 = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CREATED)
            .filter(r -> r.key() == wfInstance2)
            .findFirst()
            .get();

        assertThat(event2.value().get("bpmnProcessId")).isEqualTo("process2");
    }

    @SuppressWarnings("unchecked")
    private int extractWorkflowKey(final ExecuteCommandResponse deployment1)
    {
        final List<Map<String, Object>> deployedWorkflows = (List<Map<String, Object>>) deployment1.getValue().get("deployedWorkflows");
        return (int) deployedWorkflows.get(0).get(PROP_WORKFLOW_KEY);
    }
}
