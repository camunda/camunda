package org.camunda.tngp.broker.event.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class TopicSubscriptionProcessorTest
{

    protected static final int INITIAL_LOG_POSITION = 10;

    @FluentMock
    protected SubscribedEventWriter eventWriter;

    @Rule
    public MockStreamProcessorController<DummyEvent> controller = new MockStreamProcessorController<>(DummyEvent.class, EventType.RAFT_EVENT, INITIAL_LOG_POSITION);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldWriteEvent()
    {
        // given
        final TopicSubscriptionProcessor processor =
                new TopicSubscriptionProcessor(1, 2, eventWriter);

        controller.initStreamProcessor(processor);
        final LoggedEvent event = controller.buildLoggedEvent(14L, (e) -> e.setId(4));

        // when
        controller.processEvent(event);

        // then
        assertThat(controller.getWrittenEvents()).isEmpty();
        verify(eventWriter).channelId(1);
        verify(eventWriter).event(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
        verify(eventWriter).eventType(EventType.RAFT_EVENT);
        verify(eventWriter).longKey(14L);
        verify(eventWriter).position(INITIAL_LOG_POSITION);
        verify(eventWriter).tryWriteMessage();
    }

    public static class DummyEvent extends UnpackedObject
    {
        protected IntegerProperty idProperty = new IntegerProperty("id");

        public void setId(int id)
        {
            this.idProperty.setValue(id);
        }

        public int getId()
        {
            return idProperty.getValue();
        }

    }
}
