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
package io.zeebe.broker.it.job;

import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.cmd.ClientCommandRejectedException;

public class CompleteJobTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties p = new Properties();
        p.setProperty(ClientProperties.REQUEST_TIMEOUT_SEC, "3");
        return p;
    });

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(15);

    @Test
    public void shouldProvideReasonInExceptionMessageOnRejection()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient().jobClient();

        final JobEvent job = jobClient.newCreateCommand()
            .jobType("bar")
            .payload("{}")
            .send()
            .join();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Command (COMPLETE) for event with key " +
                job.getKey() +
                " was rejected. It is not applicable in the current state. " +
                "Job is not in state: ACTIVATED, TIMED_OUT");

        // when
        jobClient.newCompleteCommand(job)
            .send()
            .join();
    }
}
