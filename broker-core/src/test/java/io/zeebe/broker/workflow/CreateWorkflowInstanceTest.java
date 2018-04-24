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

import static io.zeebe.broker.test.MsgPackUtil.*;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_STATE;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceState.START_EVENT_OCCURRED;
import static io.zeebe.broker.workflow.data.WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.*;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_KEY;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_PAYLOAD;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.broker.protocol.clientapi.*;
import io.zeebe.util.StreamUtil;
import org.junit.*;
import org.junit.rules.RuleChain;


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
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.getEvent())
            .containsEntry(PROP_STATE, "WORKFLOW_INSTANCE_REJECTED")
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
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        final SubscribedEvent workflowEvent = testClient.receiveSingleEvent(workflowEvents("CREATED"));
        final long workflowKey = workflowEvent.key();

        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.getEvent())
            .containsEntry(PROP_STATE, WORKFLOW_INSTANCE_CREATED.name())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_KEY, workflowKey)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
    }

    @Test
    public void shouldCreateWorkflowInstanceByBpmnProcessIdAndLatestVersion()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent("foo")
                .endEvent()
                .done());

        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent("bar")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_VERSION, -1)
                .done()
                .sendAndAwait();

        // then
        final SubscribedEvent workflowEvent = testClient.receiveEvents(workflowEvents("CREATED"))
                .limit(2)
                .collect(Collectors.toList())
                .get(1);
        final long workflowKey = workflowEvent.key();

        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents(START_EVENT_OCCURRED.name()));

        assertThat(event.event())
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
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent("foo")
                .endEvent()
                .done());

        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent("bar")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_VERSION, 1)
                .done()
                .sendAndAwait();

        // then
        final SubscribedEvent workflowEvent = testClient.receiveEvents(workflowEvents("CREATED"))
                .limit(2)
                .collect(Collectors.toList())
                .get(0);
        final long workflowKey = workflowEvent.key();

        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents(START_EVENT_OCCURRED.name()));

        assertThat(event.event())
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

        testClient.deploy(Bpmn.createExecutableWorkflow("process")
              .startEvent("bar")
              .endEvent()
              .done());

        final SubscribedEvent workflowEvent = testClient.receiveEvents(workflowEvents("CREATED"))
                .limit(2)
                .collect(Collectors.toList())
                .get(1);
        final long workflowKey = workflowEvent.key();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_KEY, workflowKey)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.getEvent())
            .containsEntry(PROP_STATE, WORKFLOW_INSTANCE_CREATED.name())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 2)
            .containsEntry(PROP_WORKFLOW_KEY, workflowKey)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, resp.key());
    }

    @Test
    public void shouldCreateWorkflowInstanceByWorkflowKeyAndPreviousVersion()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent()
                .done());

        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                  .startEvent()
                  .endEvent()
                  .done());

        final SubscribedEvent workflowEvent = testClient.receiveEvents(workflowEvents("CREATED"))
                .limit(2)
                .collect(Collectors.toList())
                .get(0);
        final long workflowKey = workflowEvent.key();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_KEY, workflowKey)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.getEvent())
            .containsEntry(PROP_STATE, WORKFLOW_INSTANCE_CREATED.name())
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
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.getEvent())
            .containsEntry(PROP_STATE, WORKFLOW_INSTANCE_CREATED.name())
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
                .eventTypeWorkflow()
                .command()
                    .put(PROP_STATE, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
                    .put(PROP_WORKFLOW_PAYLOAD, invalidPayload)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(apiRule.getDefaultPartitionId());
        assertThat(resp.getEvent())
            .containsEntry(PROP_STATE, "WORKFLOW_INSTANCE_REJECTED")
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
        final List<SubscribedEvent> workflowInstanceEvents = testClient.receiveEvents(workflowInstanceEvents("WORKFLOW_INSTANCE_CREATED"))
                .limit(2)
                .collect(Collectors.toList());

        assertThat(workflowInstanceEvents.get(0).event())
            .containsEntry("bpmnProcessId", "foo")
            .containsEntry("workflowInstanceKey", workflowInstanceKeyFoo);

        assertThat(workflowInstanceEvents.get(1).event())
            .containsEntry("bpmnProcessId", "baaaar")
            .containsEntry("workflowInstanceKey", workflowInstanceKeyBaaaar);
    }

    @Test
    public void shouldCreateMultipleWorkflowInstancesForDifferentVersions()
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

        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        testClient.deploy(workflow);

        final long workflowInstance2 = testClient.createWorkflowInstance("process");


        // then
        final List<SubscribedEvent> workflowInstanceEvents = testClient.receiveEvents(workflowInstanceEvents("ACTIVITY_ACTIVATED"))
                .limit(2)
                .collect(Collectors.toList());

        assertThat(workflowInstanceEvents.get(0).event())
            .containsEntry("workflowInstanceKey", workflowInstance1)
            .containsEntry("version", 1);

        assertThat(workflowInstanceEvents.get(1).event())
            .containsEntry("workflowInstanceKey", workflowInstance2)
            .containsEntry("version", 2);

        final long createdTasks = testClient.receiveEvents(taskEvents("CREATED")).limit(2).count();
        assertThat(createdTasks).isEqualTo(2);
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

        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "CREATED");

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("yaml-workflow");

        // then
        final SubscribedEvent workflowInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_CREATED"));
        assertThat(workflowInstanceEvent.event())
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
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "CREATED");

        // when
        final long wfInstance1 = testClient.createWorkflowInstance("process1");
        final long wfInstance2 = testClient.createWorkflowInstance("process2");

        // then
        final SubscribedEvent event1 = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_CREATED", wfInstance1));
        assertThat(event1.event().get("bpmnProcessId")).isEqualTo("process1");

        final SubscribedEvent event2 = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_CREATED", wfInstance2));
        assertThat(event2.event().get("bpmnProcessId")).isEqualTo("process2");
    }

}
