/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.PollableTopicSubscription;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TopicSubscriptionTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout timeout = Timeout.seconds(10);

    protected TngpClient client;
    protected RecordingEventHandler recordingHandler;
    protected ObjectMapper objectMapper;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
        this.recordingHandler = new RecordingEventHandler();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldOpenSubscription()
    {
        // when
        final TopicSubscription subscription = client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // then
        assertThat(subscription.isOpen());

    }

    @Test
    public void shouldReceiveEventsCreatedAfterSubscription() throws IOException
    {
        // given
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // when
        final Long taskKey = client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() == 2);

        assertThat(recordingHandler.numRecordedEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsCreatedBeforeSubscription() throws IOException
    {
        // given
        final Long taskKey = client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        // when
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() == 2);

        assertThat(recordingHandler.numRecordedEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldCloseSubscription() throws InterruptedException
    {
        // given
        final TopicSubscription subscription = client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isOpen()).isFalse();

        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        Thread.sleep(1000L);
        assertThat(recordingHandler.numRecordedEvents()).isEqualTo(0);
    }

    @Test
    public void shouldOpenMultipleSubscriptionsOnSameTopic() throws IOException
    {
        // given
        final Long taskKey = client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();


        final RecordingEventHandler secondEventHandler = new RecordingEventHandler();
        client.topic(0).newSubscription()
            .handler(secondEventHandler)
            .open();

        // when
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() == 2);
        TestUtil.waitUntil(() -> secondEventHandler.numRecordedEvents() == 2);

        // then
        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
        secondEventHandler.assertTaskEvent(0, taskKey, "CREATE");
        secondEventHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldHandleOneEventAtATime() throws InterruptedException
    {
        // given
        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        final Duration handlingIntervalLength = Duration.ofSeconds(1);
        final ParallelismDetectionHandler handler = new ParallelismDetectionHandler(handlingIntervalLength);

        // when
        client.topic(0).newSubscription()
            .handler(handler)
            .open();

        // then
        final int numExpectedEvents = 2;
        Thread.sleep(handlingIntervalLength.toMillis() * numExpectedEvents);

        TestUtil.waitUntil(() -> handler.numInvocations() == numExpectedEvents);
        assertThat(handler.hasDetectedParallelism()).isFalse();
    }

    @Test
    public void shouldCreatePollableSubscription() throws IOException
    {
        // given
        final Long taskKey = client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final PollableTopicSubscription subscription = client.topic(0)
            .newPollableSubscription()
            .open();

        // when
        TestUtil.doRepeatedly(() -> subscription.poll(recordingHandler)).until((i) -> recordingHandler.numRecordedEvents() == 2);

        // then
        assertThat(recordingHandler.numRecordedEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    protected static class ParallelismDetectionHandler implements TopicEventHandler
    {

        protected AtomicBoolean executing = new AtomicBoolean(false);
        protected AtomicBoolean parallelInvocationDetected = new AtomicBoolean(false);
        protected AtomicInteger numInvocations = new AtomicInteger(0);
        protected long timeout;

        public ParallelismDetectionHandler(Duration duration)
        {
            this.timeout = duration.toMillis();
        }

        @Override
        public void handle(EventMetadata metadata, TopicEvent event) throws Exception
        {
            numInvocations.incrementAndGet();
            if (executing.compareAndSet(false, true))
            {
                try
                {
                    Thread.sleep(timeout);
                }
                finally
                {
                    executing.set(false);
                }
            }
            else
            {
                parallelInvocationDetected.set(true);
            }
        }

        public boolean hasDetectedParallelism()
        {
            return parallelInvocationDetected.get();
        }

        public int numInvocations()
        {
            return numInvocations.get();
        }

    }

    @Test
    public void testValidateTopicId()
    {
        // given
        final TngpClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("id must be greater than or equal to 0");

        // when
        client.topic(-1);
    }

}
