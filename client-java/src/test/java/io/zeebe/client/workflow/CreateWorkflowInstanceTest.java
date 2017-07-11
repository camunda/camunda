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

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.util.StringUtil.getBytes;

import java.io.ByteArrayInputStream;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;


public class CreateWorkflowInstanceTest
{
    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateWorkflowInstance()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(1L)
                .put("eventType", "WORKFLOW_INSTANCE_CREATED")
                .put("version", 1)
                .put("workflowInstanceKey", 1)
                .put("payload", msgPackConverter.convertToMsgPack("null"))
                .done()
                .register();

        // when
        final WorkflowInstance workflowInstance = clientRule.workflowTopic()
                .create()
                .bpmnProcessId("foo")
                .execute();

        // then
        assertThat(workflowInstance).isNotNull();
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
    }

    @Test
    public void shouldCreateWorkflowInstanceWithPayload() throws Exception
    {
        // given
        final byte[] payload = getBytes("{ \"bar\" : 4 }");

        brokerRule.onWorkflowRequestRespondWith(1L)
            .put("eventType", "WORKFLOW_INSTANCE_CREATED")
            .put("version", 1)
            .put("workflowInstanceKey", 1)
            .put("payload", msgPackConverter.convertToMsgPack("{ \"bar\" : 4 }"))
            .done()
            .register();

        // when
        final WorkflowInstance workflowInstance = clientRule.workflowTopic()
            .create()
            .bpmnProcessId("foo")
            .payload(new ByteArrayInputStream(payload))
            .execute();

        // then
        assertThat(workflowInstance).isNotNull();
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
        assertThat(workflowInstance.getPayload()).isEqualTo("{\"bar\":4}");
    }

    @Test
    public void shouldNotCreateWorkflowInstanceForMissingBpmnProcessId()
    {
        // expect exception
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("bpmnProcessId must not be null");

        // when create command is executed without BPMN process id
        clientRule.workflowTopic()
                .create()
                .execute();
    }

    @Test
    public void shouldRejectCreateWorkflowInstance()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(1L)
                .put("eventType", "WORKFLOW_INSTANCE_REJECTED")
                .put("bpmnProcessId", "foo")
                .put("version", 1)
                .done()
                .register();


        // expect exception
        expectedException.expect(ClientCommandRejectedException.class);
        expectedException.expectMessage("Failed to create instance of workflow with BPMN process id 'foo' and version '1'.");

        // when
        clientRule.workflowTopic()
            .create()
            .bpmnProcessId("foo")
            .execute();
    }

    @Test
    public void shouldCreateWorkflowInstanceWithVersion()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(1L)
                .put("eventType", "WORKFLOW_INSTANCE_CREATED")
                .put("workflowInstanceKey", 1)
                .put("payload", msgPackConverter.convertToMsgPack("null"))
                .done()
                .register();

        // when
        final WorkflowInstance workflowInstance = clientRule.workflowTopic()
                .create()
                .bpmnProcessId("foo")
                .version(2)
                .execute();

        // then
        assertThat(workflowInstance).isNotNull();
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(2);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
    }
}
