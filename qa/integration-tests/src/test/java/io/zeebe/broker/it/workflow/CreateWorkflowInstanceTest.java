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
package io.zeebe.broker.it.workflow;

import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfInstanceEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateWorkflowInstanceTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void deployProcess()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        workflowService.deploy()
            .bpmnModelInstance(
                Bpmn.createExecutableProcess("anId")
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();

        workflowService.deploy()
            .bpmnModelInstance(
                Bpmn.createExecutableProcess("anId")
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();
    }

    @Test
    public void shouldCreateBpmnProcessById()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // when
        final WorkflowInstance workflowInstance =
            workflowService
                .create()
                .bpmnProcessId("anId")
                .execute();

        // then instance of latest of workflow version is created
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
        assertThat(workflowInstance.getVersion()).isEqualTo(2);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldCreateBpmnProcessByIdAndVersion()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();


        // when
        final WorkflowInstance workflowInstance =
            workflowService
                .create()
                .bpmnProcessId("anId")
                .version(1)
                .execute();

        // then instance is created of first workflow version
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldCreateBpmnProcessByKey()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("CREATED")));

        final long workflowKey = eventRecorder.getSingleWorkflowEvent(wfEvent("CREATED")).getMetadata().getEventKey();

        // when
        final WorkflowInstance workflowInstance =
            workflowService
                .create()
                .workflowKey(workflowKey)
                .execute();

        // then
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowKey()).isEqualTo(workflowKey);

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldRejectCreateBpmnProcessByIllegalId()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage("Failed to create instance of workflow with BPMN process id 'illegal' and version '-1'.");

        // when
        workflowService
            .create()
            .bpmnProcessId("illegal")
            .execute();
    }

    @Test
    public void shouldRejectCreateBpmnProcessByIllegalKey()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage("Failed to create instance of workflow with workflow key '99'.");

        // when
        workflowService
            .create()
            .workflowKey(99L)
            .execute();
    }

    @Test
    public void shouldThrowExceptionForCreateBpmnProcessIfBpmnProcessIdAndWorkflowKeyNotSet()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(RuntimeException.class);
        exception.expectMessage("Can not create a workflow instance. Need to provide either a workflow key or a BPMN process id with/without version.");

        // when
        workflowService
            .create()
            .execute();
    }

    @Test
    public void shouldThrowExceptionForIllegalVersion()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(RuntimeException.class);
        exception.expectMessage("version must be greater than or equal to -1");

        // when
        workflowService
            .create()
            .bpmnProcessId("anId")
            .version(-10)
            .execute();
    }

}
