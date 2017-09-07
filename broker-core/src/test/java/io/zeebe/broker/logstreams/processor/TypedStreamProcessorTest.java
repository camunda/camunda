package io.zeebe.broker.logstreams.processor;

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.broker.topic.Events;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.topic.TestStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;

public class TypedStreamProcessorTest
{
    public static final String STREAM_NAME = "foo";
    public static final int STREAM_PROCESSOR_ID = 144144;

    public TemporaryFolder tempFolder = new TemporaryFolder();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(tempFolder).around(closeables);

    protected TestStreams streams;
    protected LogStream stream;

    @Mock
    protected ServerOutput output;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        final ActorScheduler scheduler = ActorSchedulerBuilder.createDefaultScheduler("foo");
        closeables.manage(scheduler);

        streams = new TestStreams(tempFolder.getRoot(), closeables, scheduler);

        stream = streams.createLogStream(STREAM_NAME);
    }

    @Test
    public void shouldWriteSourceEventAndProducerOnBatch()
    {
        // given
        final TypedStreamEnvironment env = new TypedStreamEnvironment(streams.getLogStream(STREAM_NAME), output)
            .withEventType(EventType.TOPIC_EVENT, TopicEvent.class);

        final TypedStreamProcessor streamProcessor = env.newStreamProcessor()
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATE, new BatchProcessor())
            .build();

        final StreamProcessorControl streamProcessorControl = streams.runStreamProcessor(STREAM_NAME, STREAM_PROCESSOR_ID, streamProcessor);
        final long firstEventPosition = streams.newEvent(STREAM_NAME).event(createTopic("foo", 1)).write();

        // when
        streamProcessorControl.unblock();

        final LoggedEvent writtenEvent = doRepeatedly(() -> streams.events(STREAM_NAME)
                .filter(Events::isTopicEvent)
                .filter(e -> Events.asTopicEvent(e).getState() == TopicState.CREATE_REJECTED)
                .findFirst())
            .until(o -> o.isPresent())
            .get();

        // then
        assertThat(writtenEvent.getProducerId()).isEqualTo(STREAM_PROCESSOR_ID);

        assertThatBuffer(writtenEvent.getSourceEventLogStreamTopicName())
            .hasBytes(STREAM_NAME.getBytes(StandardCharsets.UTF_8), writtenEvent.getSourceEventLogStreamTopicNameOffset());
        assertThat(writtenEvent.getSourceEventLogStreamPartitionId()).isEqualTo(stream.getPartitionId());
        assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(firstEventPosition);
    }

    protected TopicEvent createTopic(String name, int partitions)
    {
        final TopicEvent event = new TopicEvent();
        event.setName(BufferUtil.wrapString(name));
        event.setPartitions(partitions);
        event.setState(TopicState.CREATE);

        return event;
    }


    protected static class BatchProcessor implements TypedEventProcessor<TopicEvent>
    {

        @Override
        public void processEvent(TypedEvent<TopicEvent> event)
        {
        }

        @Override
        public long writeEvent(TypedEvent<TopicEvent> event, TypedStreamWriter writer)
        {
            final TopicEvent value = event.getValue();
            value.setState(TopicState.CREATE_REJECTED);
            return writer.newBatch().addNewEvent(value).write();
        }

    }
}
