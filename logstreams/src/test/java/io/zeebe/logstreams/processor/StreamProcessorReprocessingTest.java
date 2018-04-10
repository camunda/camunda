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
package io.zeebe.logstreams.processor;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class StreamProcessorReprocessingTest
{
    private static final String PROCESSOR_NAME = "test";
    private static final int PROCESSOR_ID = 1;
    private static final int OTHER_PROCESSOR_ID = 2;

    private static final DirectBuffer EVENT = wrapString("FOO");

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder);
    private LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(temporaryFolder)
                 .around(logStreamRule)
                 .around(writer);

    private StreamProcessorController controller;
    private RecordingStreamProcessor streamProcessor;
    private EventProcessor eventProcessor;
    private EventFilter eventFilter;
    private int partitionId;

    @Before
    public void init()
    {
        streamProcessor = RecordingStreamProcessor.createSpy();
        eventProcessor = streamProcessor.getEventProcessorSpy();

        partitionId = logStreamRule.getLogStream().getPartitionId();

        eventFilter = mock(EventFilter.class);
        when(eventFilter.applies(any())).thenReturn(true);

        controller = LogStreams.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID, streamProcessor)
            .logStream(logStreamRule.getLogStream())
            .snapshotStorage(logStreamRule.getSnapshotStorage())
            .actorScheduler(logStreamRule.getActorScheduler())
            .eventFilter(eventFilter)
            .build();
    }

    /**
     * Format: [1|S:-] --> [2|S:1]
     *
     * => two events: first event has no source event position,
     *    second event has the first event's position as source event position
     */

    @Test
    public void shouldReprocessSourceEvent()
    {
        // given [1|S:-] --> [2|S:1]
        final long eventPosition1 = writeEvent();
        final long eventPosition2 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition1));

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition1, eventPosition2);

        verify(eventProcessor, times(2)).processEvent();
        verify(eventProcessor, times(1)).executeSideEffects();
        verify(eventProcessor, times(1)).writeEvent(any());
        verify(eventProcessor, times(2)).updateState();
    }

    @Test
    public void shouldNotReprocessEventFromOtherProcessor()
    {
        // given [1|S:-] --> [2|S:1]
        final long eventPosition1 = writeEvent();
        final long eventPosition2 = writeEventWith(w -> w.producerId(OTHER_PROCESSOR_ID).sourceEvent(partitionId, eventPosition1));

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition1, eventPosition2);

        verify(eventProcessor, times(2)).processEvent();
        verify(eventProcessor, times(2)).executeSideEffects();
        verify(eventProcessor, times(2)).writeEvent(any());
        verify(eventProcessor, times(2)).updateState();
    }

    @Test
    public void shouldReprocessUntilLastSourceEvent()
    {
        // given [1|S:-] --> [2|S:1] --> [3|S:2]
        final long eventPosition1 = writeEvent();
        final long eventPosition2 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition1));
        final long eventPosition3 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition2));

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 3);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition1, eventPosition2, eventPosition3);

        verify(eventProcessor, times(3)).processEvent();
        verify(eventProcessor, times(1)).executeSideEffects();
        verify(eventProcessor, times(1)).writeEvent(any());
        verify(eventProcessor, times(3)).updateState();
    }

    @Test
    public void shouldReprocessAllEventsUntilSourceEvent()
    {
        // given [1|S:-] --> [2|S:-] --> [3|S:2]
        final long eventPosition1 = writeEvent();
        final long eventPosition2 = writeEvent();
        final long eventPosition3 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition2));

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 3);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition1, eventPosition2, eventPosition3);

        verify(eventProcessor, times(3)).processEvent();
        verify(eventProcessor, times(1)).executeSideEffects();
        verify(eventProcessor, times(1)).writeEvent(any());
        verify(eventProcessor, times(3)).updateState();
    }

    @Test
    public void shouldSkipEventIfNoEventProcessorIsProvided()
    {
        // given [1|S:-] --> [2|S:-] --> [3|S:2]
        writeEvent();
        final long eventPosition2 = writeEvent();
        final long eventPosition3 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition2));

        // return null as event processor for first event
        doReturn(null).doCallRealMethod().when(streamProcessor).onEvent(any());

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition2, eventPosition3);

        verify(eventProcessor, times(2)).processEvent();
        verify(eventProcessor, times(1)).executeSideEffects();
        verify(eventProcessor, times(1)).writeEvent(any());
        verify(eventProcessor, times(2)).updateState();
    }

    @Test
    public void shouldSkipEventIfEventFilterIsNotMet()
    {
        // given [1|S:-] --> [2|S:-] --> [3|S:2]
        writeEvent();
        final long eventPosition2 = writeEvent();
        final long eventPosition3 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition2));

        when(eventFilter.applies(any())).thenReturn(false, true, true);

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition2, eventPosition3);

        verify(eventProcessor, times(2)).processEvent();
        verify(eventProcessor, times(1)).executeSideEffects();
        verify(eventProcessor, times(1)).writeEvent(any());
        verify(eventProcessor, times(2)).updateState();
    }

    @Test
    public void shouldFailOnReprocessing()
    {
        // given [1|S:-] --> [2|S:1]
        final long eventPosition1 = writeEvent();
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition1));

        doThrow(new RuntimeException("expected")).when(eventProcessor).processEvent();

        // when
        final ActorFuture<Void> future = controller.openAsync();

        waitUntil(() -> future.isDone());

        // then
        assertThat(future.isCompletedExceptionally()).isTrue();
        assertThat(future.getException().getMessage()).contains("failed to reprocess event");

        assertThat(controller.isFailed()).isTrue();
    }

    @Test
    public void shouldNotReprocessEventsIfReadOnly()
    {
        controller = LogStreams.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID, streamProcessor)
                .logStream(logStreamRule.getLogStream())
                .snapshotStorage(logStreamRule.getSnapshotStorage())
                .actorScheduler(logStreamRule.getActorScheduler())
                .readOnly(true)
                .build();

        // given [1|S:-] --> [2|S:1]
        final long eventPosition1 = writeEvent();
        final long eventPosition2 = writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceEvent(partitionId, eventPosition1));

        // when
        controller.openAsync().join();

        waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

        // then
        assertThat(streamProcessor.getEvents())
            .extracting(LoggedEvent::getPosition)
            .containsExactly(eventPosition1, eventPosition2);

        verify(eventProcessor, times(2)).processEvent();
        verify(eventProcessor, times(2)).executeSideEffects();
        verify(eventProcessor, times(2)).writeEvent(any());
        verify(eventProcessor, times(2)).updateState();
    }

    private long writeEvent()
    {
        return writeEventWith(w ->
        { });
    }

    private long writeEventWith(final Consumer<LogStreamWriter> wr)
    {
        return writer.writeEvent(w ->
        {
            w.positionAsKey().value(EVENT);
            wr.accept(w);
        }, true);
    }

}
