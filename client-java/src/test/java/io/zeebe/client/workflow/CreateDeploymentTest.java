/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.workflow;

import static io.zeebe.protocol.clientapi.EventType.DEPLOYMENT_EVENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.ResourceType;
import io.zeebe.client.util.ClientRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest
{
    private static final WorkflowDefinition WORKFLOW_MODEL = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .done();

    private static final byte[] WORKFLOW_AS_BYTES = Bpmn.convertToString(WORKFLOW_MODEL).getBytes(UTF_8);

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldSendDeploymentRequestToSystemTopic()
    {
        // given
        brokerRule.onExecuteCommandRequest(Protocol.SYSTEM_PARTITION, DEPLOYMENT_EVENT, "CREATE_DEPLOYMENT")
            .respondWith()
            .key(2L)
            .event()
                .put("state", "DEPLOYMENT_CREATED")
                .done()
            .register();

        // when
        final DeploymentEvent deployment = clientRule.workflows().deploy("test-topic")
            .workflowModel(WORKFLOW_MODEL)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(commandRequest.getCommand()).containsEntry("topicName", "test-topic");

        assertThat(deployment.getMetadata().getTopicName()).isEqualTo(Protocol.SYSTEM_TOPIC);
    }

    @Test
    public void shouldCreateDeployment()
    {
        // given
        final List<Map<String, Object>> deployedWorkflows = new ArrayList<>();
        Map<String, Object> deployedWorkflow = new HashMap<>();
        deployedWorkflow.put("bpmnProcessId", "foo");
        deployedWorkflow.put("version", 1);
        deployedWorkflows.add(deployedWorkflow);

        deployedWorkflow = new HashMap<>();
        deployedWorkflow.put("bpmnProcessId", "bar");
        deployedWorkflow.put("version", 2);
        deployedWorkflows.add(deployedWorkflow);

        brokerRule.onExecuteCommandRequest(r -> r.eventType() == DEPLOYMENT_EVENT)
            .respondWith()
            .key(2L)
            .event()
                .put("state", "DEPLOYMENT_CREATED")
                .put("deployedWorkflows", deployedWorkflows)
                .done()
            .register();

        // when
        final DeploymentEvent deployment = clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .workflowModel(WORKFLOW_MODEL)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.key()).isEqualTo(-1);
        assertThat(commandRequest.getCommand()).containsEntry("state", "CREATE_DEPLOYMENT");

        assertThat(deployment.getMetadata().getKey()).isEqualTo(2L);

        assertThat(deployment.getDeployedWorkflows()).hasSize(2);
        assertThat(deployment.getDeployedWorkflows()).extracting("bpmnProcessId").contains("foo", "bar");
        assertThat(deployment.getDeployedWorkflows()).extracting("version").contains(1, 2);
    }

    @Test
    public void shouldRejectCreateDeployment()
    {
        // given
        brokerRule.onExecuteCommandRequest(DEPLOYMENT_EVENT, "CREATE_DEPLOYMENT")
            .respondWith()
            .key(2L)
            .event()
                .put("state", "DEPLOYMENT_REJECTED")
                .put("errorMessage", "foo")
                .done()
            .register();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Deployment was rejected: foo");

        // when
        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .workflowModel(WORKFLOW_MODEL)
            .execute();
    }

    @Test
    public void shouldDeployResourceAsWorkflowModel()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .workflowModel(WORKFLOW_MODEL)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsEntry("resource", WORKFLOW_AS_BYTES)
            .containsEntry("resourceType", "BPMN_XML");
    }

    @Test
    public void shouldDeployResourceAsString()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceStringUtf8(Bpmn.convertToString(WORKFLOW_MODEL), ResourceType.BPMN_XML)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsEntry("resource", WORKFLOW_AS_BYTES)
            .containsEntry("resourceType", "BPMN_XML");
    }

    @Test
    public void shouldDeployResourceAsBytes()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceBytes(WORKFLOW_AS_BYTES, ResourceType.BPMN_XML)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsEntry("resource", WORKFLOW_AS_BYTES)
            .containsEntry("resourceType", "BPMN_XML");
    }

    @Test
    public void shouldDeployResourceAsStream()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceStream(new ByteArrayInputStream(WORKFLOW_AS_BYTES), ResourceType.BPMN_XML)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsEntry("resource", WORKFLOW_AS_BYTES)
            .containsEntry("resourceType", "BPMN_XML");
    }

    @Test
    public void shouldDeployResourceAsXmlFile() throws Exception
    {
        // given
        stubDeploymentRequest();

        // when
        final String filePath = getClass().getResource("/workflows/one-task-process.bpmn").toURI().getPath();

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFile(filePath)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsKey("resource")
            .containsEntry("resourceType", "BPMN_XML");

        final byte[] resource = (byte[]) commandRequest.getCommand().get("resource");
        assertThat(new File(filePath)).hasBinaryContent(resource);
    }

    @Test
    public void shouldDeployResourceAsYamlFile() throws Exception
    {
        // given
        stubDeploymentRequest();

        // when
        final String filePath = getClass().getResource("/workflows/simple-workflow.yaml").toURI().getPath();

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFile(filePath)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsKey("resource")
            .containsEntry("resourceType", "YAML_WORKFLOW");

        final byte[] resource = (byte[]) commandRequest.getCommand().get("resource");
        assertThat(new File(filePath)).hasBinaryContent(resource);
    }

    @Test
    public void shouldDeployResourceFromXmlClasspath() throws Exception
    {
        // given
        stubDeploymentRequest();

        // when

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFromClasspath("workflows/one-task-process.bpmn")
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsKey("resource")
            .containsEntry("resourceType", "BPMN_XML");

        final byte[] resource = (byte[]) commandRequest.getCommand().get("resource");
        final String filePath = getClass().getResource("/workflows/one-task-process.bpmn").toURI().getPath();

        assertThat(new File(filePath)).hasBinaryContent(resource);
    }

    @Test
    public void shouldDeployResourceFromYamlClasspath() throws Exception
    {
        // given
        stubDeploymentRequest();

        // when

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFromClasspath("workflows/simple-workflow.yaml")
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand())
            .containsKey("resource")
            .containsEntry("resourceType", "YAML_WORKFLOW");

        final byte[] resource = (byte[]) commandRequest.getCommand().get("resource");
        final String filePath = getClass().getResource("/workflows/simple-workflow.yaml").toURI().getPath();

        assertThat(new File(filePath)).hasBinaryContent(resource);
    }

    @Test
    public void shouldBeNotValidIfResourceFileNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot deploy resource from file");

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFile("not existing")
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceClasspathFileNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot deploy resource from classpath");

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFromClasspath("not existing")
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceStreamNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("resource stream must not be null");

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceStream(getClass().getResourceAsStream("not existing"), ResourceType.BPMN_XML)
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceClasspathTypeIsUnknown()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot resolve type of resource 'example_file'.");

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFromClasspath("example_file")
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceFileTypeIsUnknown() throws Exception
    {
        final String filePath = getClass().getResource("/example_file").toURI().getPath();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot resolve type of resource");

        clientRule.workflows().deploy(clientRule.getDefaultTopicName())
            .resourceFile(filePath)
            .execute();
    }

    private void stubDeploymentRequest()
    {
        brokerRule.onExecuteCommandRequest(DEPLOYMENT_EVENT, "CREATE_DEPLOYMENT")
            .respondWith()
            .key(2L)
            .event()
                .put("state", "DEPLOYMENT_CREATED")
                .done()
            .register();
    }

}
