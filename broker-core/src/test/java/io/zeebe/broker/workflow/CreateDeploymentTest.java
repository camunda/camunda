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

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;
import java.util.*;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.util.StreamUtil;
import org.assertj.core.util.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest
{
    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateDeploymentWithBpmnXml()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", Collections.singletonList(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn")))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "CREATED");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnDeployedWorkflowDefinitions()
    {
        // when
        ExecuteCommandResponse resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW);

        // then
        List<Map<String, Object>> deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 1);

        // when deploy the workflow definition a second time
        resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW);

        // then the workflow definition version is increased
        deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 2);
    }

    @Test
    public void shouldCreateDeploymentResourceWithMultipleWorkflows() throws IOException
    {
        // given
        final InputStream resourceAsStream = getClass().getResourceAsStream("/workflows/collaboration.bpmn");

        // when
        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    StreamUtil.read(resourceAsStream),
                                    ResourceType.BPMN_XML.name(),
                                    "collaboration.bpmn");

        // then
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "CREATED");

        final List<Map<String, Object>> deployedWorkflows = Arrays.asList(getDeployedWorkflow(resp, 0), getDeployedWorkflow(resp, 1));

        assertThat(deployedWorkflows)
            .extracting(s -> s.get(PROP_WORKFLOW_BPMN_PROCESS_ID))
            .contains("process1", "process2");
    }

    @Test
    public void shouldRejectDeploymentIfTopicNotExists()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.topic().deployWithResponse("not-existing", WORKFLOW);

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).isEqualTo("No topic found with name not-existing");
    }

    @Test
    public void shouldRejectDeploymentIfNotValid()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process").done();

        // when
        final ExecuteCommandResponse resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldRejectDeploymentIfOneResourceIsNotValid()
    {
        // given
        final WorkflowDefinition invalidDefinition = Bpmn.createExecutableWorkflow("process").done();

        final List<Map<String, Object>> resources = Arrays.asList(deploymentResource(bpmnXml(invalidDefinition), "process2.bpmn"));

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", resources)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage"))
            .contains("Resource 'process2.bpmn':")
            .contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldRejectDeploymentIfNoResources()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", Collections.emptyList())
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).isEqualTo("Deployment doesn't contain a resource to deploy.");
    }

    @Test
    public void shouldRejectDeploymentIfNotParsable()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    "not a workflow".getBytes(UTF_8),
                                    ResourceType.BPMN_XML.name(),
                                    "invalid.bpmn");

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage"))
            .contains("Failed to deploy resource 'invalid.bpmn':")
            .contains("Failed to read BPMN model");
    }

    @Test
    public void shouldRejectDeploymentIfConditionIsInvalid()
    {
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("workflow")
                                     .startEvent()
                                     .exclusiveGateway()
                                     .sequenceFlow(s -> s.condition("foobar"))
                                         .endEvent()
                                     .sequenceFlow(s -> s.defaultFlow())
                                         .endEvent()
                                         .done();

        // when
        final ExecuteCommandResponse resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("The condition 'foobar' is not valid");
    }

    @Test
    public void shouldCreateDeploymentWithYamlWorfklow() throws Exception
    {
        // given
        final File yamlFile = new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
        final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

        // when
        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    yamlWorkflow.getBytes(UTF_8),
                                    ResourceType.YAML_WORKFLOW.name(),
                                    "simple-workflow.yaml");

        // then
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "CREATED");

        final Map<String, Object> deployedWorkflow = getDeployedWorkflow(resp, 0);

        assertThat(deployedWorkflow)
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "yaml-workflow");

    }

    @Test
    public void shouldAssignWorkflowVersionsPerTopic()
    {
        // given
        apiRule.createTopic("foo", 1);
        apiRule.createTopic("bar", 1);

        // when
        final ExecuteCommandResponse d1 = apiRule.topic().deployWithResponse("foo", WORKFLOW);
        final ExecuteCommandResponse d2 = apiRule.topic().deployWithResponse("bar", WORKFLOW);

        // then

        final Map<String, Object> workflow1 = getDeployedWorkflow(d1, 0);
        assertThat(workflow1.get("version")).isEqualTo(1);

        final Map<String, Object> workflow2 = getDeployedWorkflow(d2, 0);
        assertThat(workflow2.get("version")).isEqualTo(1);
    }

    private Map<String, Object> deploymentResource(final byte[] resource, String name)
    {
        final Map<String, Object> deploymentResource = new HashMap<>();
        deploymentResource.put("resource", resource);
        deploymentResource.put("resourceType", ResourceType.BPMN_XML);
        deploymentResource.put("resourceName", name);

        return deploymentResource;
    }

    private byte[] bpmnXml(final WorkflowDefinition definition)
    {
        return Bpmn.convertToString(definition).getBytes(UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDeployedWorkflow(final ExecuteCommandResponse d1, int offset)
    {
        final List<Map<String, Object>> d1Workflows = (List<Map<String, Object>>) d1.getEvent().get("deployedWorkflows");
        return d1Workflows.get(offset);
    }
}
