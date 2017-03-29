package org.camunda.tngp.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateDeployment()
    {
        // given
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put("eventType", "CREATE_DEPLOYMENT")
                    .put("bpmnXml", Bpmn.convertToString(modelInstance))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.topicId()).isEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry("eventType", "DEPLOYMENT_CREATED");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnDeployedWorkflowDefinitions()
    {
        // given
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .topicId(0)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
                .put("eventType", "CREATE_DEPLOYMENT")
                .put("bpmnXml", Bpmn.convertToString(modelInstance))
            .done()
            .sendAndAwait();

        // then
        List<Map<String, Object>> deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry("bpmnProcessId", "process");
        assertThat(deployedWorkflows.get(0)).containsEntry("version", 1);

        // when deploy the workflow definition a second time
        resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put("eventType", "CREATE_DEPLOYMENT")
                    .put("bpmnXml", Bpmn.convertToString(modelInstance))
                .done()
                .sendAndAwait();

        // then the workflow definition version is increased
        deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry("bpmnProcessId", "process");
        assertThat(deployedWorkflows.get(0)).containsEntry("version", 2);
    }

    @Test
    public void shouldRejectDeployment()
    {
        // given
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process").done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put("eventType", "CREATE_DEPLOYMENT")
                    .put("bpmnXml", Bpmn.convertToString(modelInstance))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.topicId()).isEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry("eventType", "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("The process must contain at least one none start event.");
    }

}
