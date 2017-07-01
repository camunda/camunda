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
package io.zeebe.broker.task.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueLockStreamProcessorServiceName;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.processor.LockTaskStreamProcessor;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceBuilder;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.test.util.FluentMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionManagerTest
{
    private static final ServiceName<LogStream> LOG_STREAM_SERVICE_NAME = ServiceName.newServiceName("mock-log-stream", LogStream.class);

    public static final String LOG_STREAM_TOPIC_NAME = "task-test-topic";
    private static final DirectBuffer LOG_STREAM_TOPIC_NAME_BUFFER = wrapString(LOG_STREAM_TOPIC_NAME);
    private static final int LOG_STREAM_PARTITION_ID = 2;
    private static final String LOG_STREAM_LOG_NAME = String.format("%s.%d", LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID);

    public static final String ANOTHER_LOG_STREAM_TOPIC_NAME = "task-test-topic-2";
    private static final DirectBuffer ANOTHER_LOG_STREAM_TOPIC_NAME_BUFFER = wrapString(ANOTHER_LOG_STREAM_TOPIC_NAME);
    private static final int ANOTHER_LOG_STREAM_PARTITION_ID = 3;
    private static final String ANOTHER_LOG_STREAM_LOG_NAME = String.format("%s.%d", ANOTHER_LOG_STREAM_TOPIC_NAME, ANOTHER_LOG_STREAM_PARTITION_ID);

    private static final String TASK_TYPE = "test-task";
    private static final DirectBuffer TASK_TYPE_BUFFER = wrapString(TASK_TYPE);

    private static final String ANOTHER_TASK_TYPE = "another-task";
    private static final DirectBuffer ANOTHER_TASK_TYPE_BUFFER = wrapString(ANOTHER_TASK_TYPE);

    @FluentMock
    private ServiceStartContext mockServiceContext;

    @FluentMock
    private ServiceBuilder<Object> mockServiceBuilder;

    @Mock
    private Function<DirectBuffer, LockTaskStreamProcessor> mockStreamProcessorBuilder;

    private LogStream mockLogStream;
    private LockTaskStreamProcessor mockStreamProcessor;

    private TaskSubscriptionManager manager;

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private TaskSubscription subscription;

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);

        mockLogStream = createMockLogStream(LOG_STREAM_TOPIC_NAME_BUFFER, LOG_STREAM_PARTITION_ID);

        when(mockServiceBuilder.install()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockServiceContext.createService(any(), any())).thenReturn(mockServiceBuilder);
        when(mockServiceContext.removeService(any())).thenReturn(CompletableFuture.completedFuture(null));

        mockStreamProcessor = createMockStreamProcessor(LOG_STREAM_TOPIC_NAME_BUFFER, LOG_STREAM_PARTITION_ID, TASK_TYPE_BUFFER);

        manager = new TaskSubscriptionManager(mockServiceContext, mockStreamProcessorBuilder);

        subscription = new TaskSubscription()
            .setTopicName(LOG_STREAM_TOPIC_NAME_BUFFER).setPartitionId(LOG_STREAM_PARTITION_ID).setTaskType(TASK_TYPE_BUFFER);
    }

    private LockTaskStreamProcessor createMockStreamProcessor(DirectBuffer logStreamTopicName, int logStreamPartitionId, DirectBuffer taskTypeBuffer)
    {
        final LockTaskStreamProcessor mockStreamProcessor = mock(LockTaskStreamProcessor.class);

        when(mockStreamProcessorBuilder.apply(taskTypeBuffer)).thenReturn(mockStreamProcessor);
        when(mockStreamProcessor.getLogStreamTopicName()).thenReturn(logStreamTopicName);
        when(mockStreamProcessor.getLogStreamPartitionId()).thenReturn(logStreamPartitionId);
        when(mockStreamProcessor.getSubscriptedTaskType()).thenReturn(taskTypeBuffer);

        when(mockStreamProcessor.addSubscription(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockStreamProcessor.increaseSubscriptionCreditsAsync(any())).thenReturn(true);
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        return mockStreamProcessor;
    }

    private LogStream createMockLogStream(DirectBuffer logStreamTopicName, int logStreamPartitionId)
    {
        final LogStream mockLogStream = mock(LogStream.class);

        final String logStreamName = String.format("%s.%d", bufferAsString(logStreamTopicName), logStreamPartitionId);

        when(mockLogStream.getTopicName()).thenReturn(logStreamTopicName);
        when(mockLogStream.getPartitionId()).thenReturn(logStreamPartitionId);
        when(mockLogStream.getLogName()).thenReturn(logStreamName);

        return mockLogStream;
    }

    @Test
    public void shouldCreateServiceAndAddSubscription() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(subscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(subscription.getSubscriberKey()).isEqualTo(0L);

        verify(mockStreamProcessorBuilder).apply(TASK_TYPE_BUFFER);
        verify(mockStreamProcessor).addSubscription(subscription);

        verify(mockServiceContext).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, TASK_TYPE)), any());
        verify(mockServiceBuilder).install();
    }

    @Test
    public void shouldAddSubscription() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription()
            .setTopicName(LOG_STREAM_TOPIC_NAME_BUFFER).setPartitionId(LOG_STREAM_PARTITION_ID).setTaskType(TASK_TYPE_BUFFER);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(anotherSubscription.getSubscriberKey()).isEqualTo(1L);

        verify(mockStreamProcessorBuilder, times(1)).apply(TASK_TYPE_BUFFER);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(mockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, TASK_TYPE)), any());
        verify(mockServiceBuilder, times(1)).install();
    }

    @Test
    public void shouldCreateServiceForEachLogStream() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription()
            .setTopicName(ANOTHER_LOG_STREAM_TOPIC_NAME_BUFFER).setPartitionId(ANOTHER_LOG_STREAM_PARTITION_ID).setTaskType(TASK_TYPE_BUFFER);

        final LogStream anotherMockLogStream = createMockLogStream(ANOTHER_LOG_STREAM_TOPIC_NAME_BUFFER, ANOTHER_LOG_STREAM_PARTITION_ID);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addStream(anotherMockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(anotherSubscription.getSubscriberKey()).isEqualTo(1L);

        verify(mockStreamProcessorBuilder, times(2)).apply(TASK_TYPE_BUFFER);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(mockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, TASK_TYPE)), any());
        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(ANOTHER_LOG_STREAM_LOG_NAME, TASK_TYPE)), any());
        verify(mockServiceBuilder, times(2)).install();
    }

    @Test
    public void shouldCreateServiceForEachTaskType() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription()
            .setTopicName(LOG_STREAM_TOPIC_NAME_BUFFER).setPartitionId(LOG_STREAM_PARTITION_ID).setTaskType(ANOTHER_TASK_TYPE_BUFFER);

        final LockTaskStreamProcessor anotherMockStreamProcessor = createMockStreamProcessor(LOG_STREAM_TOPIC_NAME_BUFFER, LOG_STREAM_PARTITION_ID, ANOTHER_TASK_TYPE_BUFFER);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(anotherSubscription.getSubscriberKey()).isEqualTo(1L);

        verify(mockStreamProcessorBuilder, times(1)).apply(TASK_TYPE_BUFFER);
        verify(mockStreamProcessorBuilder, times(1)).apply(ANOTHER_TASK_TYPE_BUFFER);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(anotherMockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, TASK_TYPE)), any());
        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, ANOTHER_TASK_TYPE)), any());
        verify(mockServiceBuilder, times(2)).install();
    }

    @Test
    public void shouldIncreateSubscriptionCredits() throws Exception
    {
        // given
        subscription.setCredits(2);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        manager.increaseSubscriptionCreditsAsync(new CreditsRequest(0L, 5));

        manager.doWork();

        // then
        verify(mockStreamProcessor).increaseSubscriptionCreditsAsync(new CreditsRequest(0L, 5));
    }

    @Test
    public void shouldRemoveLastSubscriptionAndRemoveService() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(0L);

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).removeSubscription(0L);

        verify(mockServiceContext).removeService(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, TASK_TYPE));
    }

    @Test
    public void shouldRemoveSubscription() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(true));

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(0L);

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).removeSubscription(0L);

        verify(mockServiceContext, never()).removeService(taskQueueLockStreamProcessorServiceName(LOG_STREAM_LOG_NAME, TASK_TYPE));
    }

    @Test
    public void shouldIgnoreRemoveSubscriptionIfNotExist() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription()
            .setTopicName(ANOTHER_LOG_STREAM_TOPIC_NAME_BUFFER).setPartitionId(ANOTHER_LOG_STREAM_PARTITION_ID).setTaskType(TASK_TYPE_BUFFER);

        manager.addSubscription(anotherSubscription);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(3L);

        manager.doWork();

        // then
        assertThat(future).isCompleted();
    }

    @Test
    public void shouldFailToAddSubscriptionIfLogStreamNotExist() throws Exception
    {
        // when
        final CompletableFuture<Void> future = manager.addSubscription(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Topic with name '%s' and partition id '%d' not found.", LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID);

        verify(mockStreamProcessor, never()).addSubscription(subscription);

        verify(mockServiceContext, never()).createService(any(), any());
        verify(mockServiceBuilder, never()).install();
    }

    @Test
    public void shouldPropagateFailureWhileAddSubscriptionAfterCreateService() throws Exception
    {
        // given
        when(mockStreamProcessor.addSubscription(subscription)).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("foo");
    }

    @Test
    public void shouldPropagateFailureWhileAddSubscription() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription()
            .setTopicName(LOG_STREAM_TOPIC_NAME_BUFFER).setPartitionId(LOG_STREAM_PARTITION_ID).setTaskType(TASK_TYPE_BUFFER);

        when(mockStreamProcessor.addSubscription(anotherSubscription)).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("foo");
    }

    @Test
    public void shouldPropagateFailureWhileAddSubscriptionIfFailToCreateService() throws Exception
    {
        // given
        when(mockServiceBuilder.install()).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("foo");
    }

    @Test
    public void shouldNotIncreaseSubscriptionCreditsIfNotExist() throws Exception
    {
        // given
        manager.addSubscription(subscription);

        // when
        manager.increaseSubscriptionCreditsAsync(new CreditsRequest(3L, 2));
        manager.doWork();

        // then
        verify(mockStreamProcessor, never()).increaseSubscriptionCreditsAsync(any());
    }

    @Test
    public void shouldFailToIncreaseSubscriptionCreditsIfLogStreamIsRemoved() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        manager.removeStream(mockLogStream);

        // when
        manager.increaseSubscriptionCreditsAsync(new CreditsRequest(0L, 5));
        manager.doWork();

        // then
        verify(mockStreamProcessor, never()).increaseSubscriptionCreditsAsync(any());
    }

    @Test
    public void shouldSignalBackpressure() throws Exception
    {
        // given
        for (int i = 0; i < manager.getCreditRequestCapacityUpperBound() - 1; i++)
        {
            manager.increaseSubscriptionCreditsAsync(new CreditsRequest(0L, 5));
        }

        // when exhausting the capacity
        final boolean success = manager.increaseSubscriptionCreditsAsync(new CreditsRequest(0L, 5));

        // then
        assertThat(success).isFalse();
    }

    @Test
    public void shouldRetrySchedulingCreditsWhenStreamProcessorIsBusy() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.increaseSubscriptionCreditsAsync(any())).thenReturn(false, false, true);

        manager.increaseSubscriptionCreditsAsync(new CreditsRequest(0L, 5));

        // when four work loops are made
        manager.doWork();
        manager.doWork();
        manager.doWork();
        manager.doWork();

        // then the submission was successful the third time
        verify(mockStreamProcessor, times(3)).increaseSubscriptionCreditsAsync(any());
    }

    @Test
    public void shouldPropagateFailureWhileRemoveSubscription() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));
        when(mockServiceContext.removeService(any())).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(0L);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("foo");
    }

    private static CompletableFuture<Void> completedExceptionallyFuture(Throwable t)
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(t);

        return future;
    }
}
