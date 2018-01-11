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
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.regex.Pattern;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.startup.BrokerRestartTest;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.test.util.TestFileUtil;
import io.zeebe.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class PersistentTopicSubscriptionTest
{

    protected static InputStream persistentBrokerConfig(String path)
    {
        final String canonicallySeparatedPath = path.replaceAll(Pattern.quote(File.separator), "/");

        return TestFileUtil.readAsTextFileAndReplace(
                BrokerRestartTest.class.getClassLoader().getResourceAsStream("persistent-broker.cfg.toml"),
                StandardCharsets.UTF_8,
                Collections.singletonMap("brokerFolder", canonicallySeparatedPath));
    }


    public TemporaryFolder tempFolder = new TemporaryFolder();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> persistentBrokerConfig(tempFolder.getRoot().getAbsolutePath()));

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(tempFolder)
        .around(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;
    protected RecordingEventHandler recordingHandler;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
        this.recordingHandler = new RecordingEventHandler();
    }

    @Test
    public void shouldResumeSubscriptionOnRestart()
    {
        // given a first task
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        final String subscriptionName = "foo";

        final TopicSubscription subscription = clientRule.topics()
            .newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(subscriptionName)
            .startAtHeadOfTopic()
            .open();

        // that was received by the subscription
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        subscription.close();

        final long lastEventPosition = recordingHandler.getRecordedEvents()
                .get(recordingHandler.numRecordedEvents() - 1)
                .getMetadata()
                .getPosition();

        recordingHandler.reset();

        // and a second not-yet-received task
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        // when
        restartBroker();

        clientRule.topics()
                .newSubscription(clientRule.getDefaultTopic())
                .handler(recordingHandler)
                .name(subscriptionName)
                .startAtHeadOfTopic()
                .open();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() > 0);

        final long firstEventPositionAfterReopen = recordingHandler.getRecordedEvents()
                .get(0)
                .getMetadata()
                .getPosition();

        assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
    }


    protected void restartBroker()
    {
        brokerRule.restartBroker();
    }

}
