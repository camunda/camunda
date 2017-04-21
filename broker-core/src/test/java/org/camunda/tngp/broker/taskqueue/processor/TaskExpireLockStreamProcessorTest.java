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
package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.test.util.BufferAssert.assertThatBuffer;
import static org.camunda.tngp.util.StringUtil.getBytes;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.test.WrittenEvent;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskExpireLockStreamProcessorTest
{
    private static final int STREAM_PROCESSOR_ID = 2;
    private static final int TARGET_LOG_STREAM_ID = 3;
    private static final long INITIAL_POSITION = 10L;

    private static final byte[] TASK_TYPE = getBytes("test-task");
    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private static final long LOCK_TIME = ClockUtil.getCurrentTimeInMillis();
    private static final Instant BEFORE_LOCK_TIME = Instant.ofEpochMilli(LOCK_TIME).minusSeconds(60);
    private static final Instant AFTER_LOCK_TIME = Instant.ofEpochMilli(LOCK_TIME).plusSeconds(60);

    private TaskExpireLockStreamProcessor streamProcessor;

    private LogStreamWriter mockLogStreamWriter;

    @Mock
    private LoggedEvent mockLoggedEvent;

    @Mock
    private LogStream mockTargetLogStream;

    @Mock
    private LogStreamReader mockTargetLogStreamReader;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public MockStreamProcessorController<TaskEvent> mockController = new MockStreamProcessorController<>(TaskEvent.class, event -> event
            .setType(TASK_TYPE_BUFFER)
            .setLockTime(LOCK_TIME)
            .setLockOwner(3),
            TASK_EVENT,
            INITIAL_POSITION);

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockTargetLogStream.getId()).thenReturn(TARGET_LOG_STREAM_ID);

        streamProcessor = new TaskExpireLockStreamProcessor();

        final StreamProcessorContext streamProcessorContext = new StreamProcessorContext();
        streamProcessorContext.setId(STREAM_PROCESSOR_ID);
        streamProcessorContext.setTargetStream(mockTargetLogStream);
        streamProcessorContext.setTargetLogStreamReader(mockTargetLogStreamReader);

        mockController.initStreamProcessor(streamProcessor, streamProcessorContext);

        mockLogStreamWriter = streamProcessorContext.getLogStreamWriter();
    }

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }

    @Test
    public void shoudExpireLockIfAfterLockTime()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        final LoggedEvent lockedEvent = mockController.buildLoggedEvent(2L, event -> event
                .setEventType(TaskEventType.LOCKED));

        mockController.processEvent(lockedEvent);

        when(mockTargetLogStreamReader.seek(INITIAL_POSITION)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true);
        when(mockTargetLogStreamReader.next()).thenReturn(lockedEvent);

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.EXPIRE_LOCK);
        assertThatBuffer(taskEvent.getType()).hasBytes(TASK_TYPE_BUFFER);

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);

        verify(mockLogStreamWriter).key(2L);
        verify(mockLogStreamWriter).producerId(STREAM_PROCESSOR_ID);
        verify(mockLogStreamWriter).sourceEvent(TARGET_LOG_STREAM_ID, INITIAL_POSITION);
    }

    @Test
    public void shoudExpireLockOnlyOnce()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        final LoggedEvent lockedEvent = mockController.buildLoggedEvent(2L, event -> event
                .setEventType(TaskEventType.LOCKED));

        mockController.processEvent(lockedEvent);

        when(mockTargetLogStreamReader.seek(INITIAL_POSITION)).thenReturn(true);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(true);
        when(mockTargetLogStreamReader.next()).thenReturn(lockedEvent);

        // when
        streamProcessor.checkLockExpirationAsync();
        mockController.drainCommandQueue();

        streamProcessor.checkLockExpirationAsync();
        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).hasSize(1);
    }

    @Test
    public void shoudNotExpireLockIfBeforeLockTime()
    {
        // given
        ClockUtil.setCurrentTime(BEFORE_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.LOCKED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldNotExpireLockIfCompleted()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.LOCKED));

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.COMPLETED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldNotExpireLockIfMarkedAsFailed()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.LOCKED));

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.FAILED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldNotExpireLockIfAlreadyExpired()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.LOCKED));

        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.LOCK_EXPIRED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldFailToExpireLockTimeIfEventNotFound()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCKED));

        when(mockTargetLogStreamReader.seek(INITIAL_POSITION)).thenReturn(false);
        when(mockTargetLogStreamReader.hasNext()).thenReturn(false);

        // when
        streamProcessor.checkLockExpirationAsync();

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Failed to check the task lock expiration time");

        mockController.drainCommandQueue();
    }

}
