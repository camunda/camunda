/**
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

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.util.ClientRule;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest
{
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
    public void shouldCancelWorkflowInstance()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(2L)
                .put("eventType", "WORKFLOW_INSTANCE_CANCELED")
                .done()
                .register();

        // when
        clientRule.workflowTopic()
                .cancel()
                    .workflowInstanceKey(2L)
                .execute();

        // then
        assertThat(brokerRule.getReceivedCommandRequests()).hasSize(1);

        final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(commandRequest.getCommand()).containsEntry("eventType", "CANCEL_WORKFLOW_INSTANCE");
        assertThat(commandRequest.key()).isEqualTo(2L);
    }

    @Test
    public void shouldFailCancelWorkflowInstanceIfKeyMissing()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("workflow instance key must be greater than 0");

        // when
        clientRule.workflowTopic()
                .cancel()
                .execute();
    }

    @Test
    public void shouldRejectCancelWorkflowInstance()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(2L)
                .put("eventType", "CANCEL_WORKFLOW_INSTANCE_REJECTED")
                .done()
                .register();

        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Failed to cancel workflow instance with key '2'.");

        // when
        clientRule.workflowTopic()
            .cancel()
                .workflowInstanceKey(2L)
            .execute();
    }

}
