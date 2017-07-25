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
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.workflow.cmd.DeploymentResult;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateDeploymentCmdTest
{
    private static final BpmnModelInstance BPMN_MODEL_INSTANCE = Bpmn.createExecutableProcess("process")
            .startEvent()
            .done();

    private static final byte[] WORKFLOW_AS_BYTES = Bpmn.convertToString(BPMN_MODEL_INSTANCE).getBytes(UTF_8);

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
                .put("eventType", "DEPLOYMENT_CREATED")
                .put("deployedWorkflows", deployedWorkflows)
                .done()
            .register();

        // when
        final DeploymentResult deploymentResult = clientRule.workflowTopic().deploy()
            .bpmnModelInstance(BPMN_MODEL_INSTANCE)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.key()).isEqualTo(-1);
        assertThat(commandRequest.getCommand()).containsEntry("eventType", "CREATE_DEPLOYMENT");

        assertThat(deploymentResult.isDeployed()).isTrue();
        assertThat(deploymentResult.getKey()).isEqualTo(2L);

        assertThat(deploymentResult.getDeployedWorkflows()).hasSize(2);
        assertThat(deploymentResult.getDeployedWorkflows()).extracting("bpmnProcessId").contains("foo", "bar");
        assertThat(deploymentResult.getDeployedWorkflows()).extracting("version").contains(1, 2);
    }

    @Test
    public void shouldRejectCreateDeployment()
    {
        // given
        brokerRule.onExecuteCommandRequest(r -> r.eventType() == DEPLOYMENT_EVENT)
            .respondWith()
            .key(2L)
            .event()
                .put("eventType", "DEPLOYMENT_REJECTED")
                .put("errorMessage", "foo")
                .done()
            .register();

        // when
        final DeploymentResult deploymentResult = clientRule.workflowTopic().deploy()
            .bpmnModelInstance(BPMN_MODEL_INSTANCE)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        assertThat(deploymentResult.isDeployed()).isFalse();
        assertThat(deploymentResult.getKey()).isEqualTo(2L);
        assertThat(deploymentResult.getErrorMessage()).isEqualTo("foo");
    }

    @Test
    public void shouldDeployResourceAsBpmnModelInstance()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(BPMN_MODEL_INSTANCE)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsEntry("bpmnXml", WORKFLOW_AS_BYTES);
    }

    @Test
    public void shouldDeployResourceAsString()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflowTopic().deploy()
            .resourceString(Bpmn.convertToString(BPMN_MODEL_INSTANCE))
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsEntry("bpmnXml", WORKFLOW_AS_BYTES);
    }

    @Test
    public void shouldDeployResourceAsBytes()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflowTopic().deploy()
            .resourceBytes(WORKFLOW_AS_BYTES)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsEntry("bpmnXml", WORKFLOW_AS_BYTES);
    }

    @Test
    public void shouldDeployResourceAsStream()
    {
        // given
        stubDeploymentRequest();

        // when
        clientRule.workflowTopic().deploy()
            .resourceStream(new ByteArrayInputStream(WORKFLOW_AS_BYTES))
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsEntry("bpmnXml", WORKFLOW_AS_BYTES);
    }

    @Test
    public void shouldDeployResourceAsFile() throws Exception
    {
        // given
        stubDeploymentRequest();

        // when
        final String filePath = getClass().getResource("/workflow/one-task-process.bpmn").toURI().getPath();

        clientRule.workflowTopic().deploy()
            .resourceFile(filePath)
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsKey("bpmnXml");

        final byte[] bpmnXml = (byte[]) commandRequest.getCommand().get("bpmnXml");
        assertThat(new File(filePath)).hasBinaryContent(bpmnXml);
    }

    @Test
    public void shouldDeployResourceFromClasspath() throws Exception
    {
        // given
        stubDeploymentRequest();

        // when

        clientRule.workflowTopic().deploy()
            .resourceFromClasspath("workflow/one-task-process.bpmn")
            .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsKey("bpmnXml");

        final byte[] bpmnXml = (byte[]) commandRequest.getCommand().get("bpmnXml");
        final String filePath = getClass().getResource("/workflow/one-task-process.bpmn").toURI().getPath();

        assertThat(new File(filePath)).hasBinaryContent(bpmnXml);
    }



    @Test
    public void shouldBeNotValidIfNoResourceSet()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("resource must not be null");

        clientRule.workflowTopic().deploy()
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceFileNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot deploy resource from file");

        clientRule.workflowTopic().deploy()
            .resourceFile("not existing")
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceClasspathFileNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot deploy resource from classpath");

        clientRule.workflowTopic().deploy()
            .resourceFromClasspath("not existing")
            .execute();
    }

    @Test
    public void shouldBeNotValidIfResourceStreamNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("resource stream must not be null");

        clientRule.workflowTopic().deploy()
            .resourceStream(getClass().getResourceAsStream("not existing"))
            .execute();
    }

    private void stubDeploymentRequest()
    {
        brokerRule.onExecuteCommandRequest(r -> r.eventType() == DEPLOYMENT_EVENT)
            .respondWith()
            .key(2L)
            .event()
                .put("eventType", "DEPLOYMENT_CREATED")
                .done()
            .register();
    }

}
