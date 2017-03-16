package org.camunda.tngp.broker.event.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.util.FluentMock;
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
    public MockStreamProcessorController<TopicSubscriptionAck> controller = new MockStreamProcessorController<>(TopicSubscriptionAck.class, EventType.RAFT_EVENT, INITIAL_LOG_POSITION);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotAckMoreThanOneEventConcurrently()
    {
        // given
        final TopicSubscriptionProcessor processor =
                new TopicSubscriptionProcessor(1, 2, 3, 0, "sub", 5, eventWriter);

        controller.initStreamProcessor(processor);

        // an ongoing ack; waiting to be confirmed
        final CompletableFuture<Void> firstAckFuture = processor.acknowledgeEventPosition(123L);
        controller.drainCommandQueue();

        // when
        final CompletableFuture<Void> secondAckFuture = processor.acknowledgeEventPosition(456L);
        controller.drainCommandQueue();

        // then
        assertThat(firstAckFuture).isNotDone();
        assertThat(secondAckFuture).isCompletedExceptionally();
        assertThat(secondAckFuture).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Cannot acknowledge; acknowledgement currently in progress");

        assertThat(controller.getWrittenEvents()).hasSize(1);
        assertThat(controller.getLastWrittenEventValue().getAckPosition()).isEqualByComparingTo(123L);
    }

}
