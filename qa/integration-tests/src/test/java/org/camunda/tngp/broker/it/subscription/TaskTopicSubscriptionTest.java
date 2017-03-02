package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TaskEvent;
import org.camunda.tngp.client.event.TaskEventHandler;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class TaskTopicSubscriptionTest
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

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldReceiveTaskPOJOEvents()
    {
        // given
        client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .retries(2)
                .execute();

        final RecordingTaskPOJOEventHandler handler = new RecordingTaskPOJOEventHandler();

        // when
        client.taskTopic(0).newSubscription()
            .startAtHeadOfTopic()
            .taskEventHandler(handler)
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() == 2);

        final TaskEvent event1 = handler.getEvent(0);
        assertThat(event1.getEvent()).isEqualTo("CREATE");
        assertThat(event1.getHeaders()).containsExactly(entry("key", "value"));
        assertThat(event1.getLockExpirationTime()).isNotNull(); // => https://github.com/camunda-tngp/camunda-tngp/issues/158
        assertThat(event1.getLockOwner()).isNotNull();
        assertThat(event1.getRetries()).isEqualTo(2);
        assertThat(event1.getType()).isEqualTo("foo");
        assertThat(event1.getPayload()).isEqualTo("{}");

        final TaskEvent event2 = handler.getEvent(1);
        assertThat(event2.getEvent()).isEqualTo("CREATED");
    }


    @Test
    public void shouldInvokeDefaultHandler() throws IOException
    {
        // given
        final long taskKey = client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final RecordingEventHandler handler = new RecordingEventHandler();

        // when no POJO handler is registered
        client.taskTopic(0).newSubscription()
            .startAtHeadOfTopic()
            .defaultHandler(handler)
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedTaskEvents() == 2);

        handler.assertTaskEvent(0, taskKey, "CREATE");
        handler.assertTaskEvent(1, taskKey, "CREATED");
    }

    protected static class RecordingTaskPOJOEventHandler implements TaskEventHandler
    {
        protected List<EventMetadata> metadata = new ArrayList<>();
        protected List<TaskEvent> events = new ArrayList<>();

        @Override
        public void handle(EventMetadata metadata, TaskEvent event) throws Exception
        {
            this.metadata.add(metadata);
            this.events.add(event);
        }

        public EventMetadata getMetadata(int index)
        {
            return metadata.get(index);
        }

        public TaskEvent getEvent(int index)
        {
            return events.get(index);
        }

        public int numRecordedEvents()
        {
            return events.size();
        }

    }
}
