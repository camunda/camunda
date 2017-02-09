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
package org.camunda.tngp.broker.taskqueue.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueLockStreamProcessorServiceName;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.TaskSubscriptionManager;
import org.camunda.tngp.broker.taskqueue.processor.LockTaskStreamProcessor;
import org.camunda.tngp.broker.taskqueue.processor.TaskSubscription;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.StreamContext;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionManagerTest
{
    private static final ServiceName<LogStream> LOG_STREAM_SERVICE_NAME = ServiceName.newServiceName("mock-log-stream", LogStream.class);

    private static final int LOG_STREAM_ID = 2;
    private static final int ANOTHER_LOG_STREAM_ID = 3;

    private static final String LOG_STREAM_NAME = "task-test-log";
    private static final String ANOTHER_LOG_STREAM_NAME = "task-test-log-2";

    private static final String TASK_TYPE = "test-task";
    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE.getBytes(StandardCharsets.UTF_8));

    private static final String ANOTHER_TASK_TYPE = "another-task";
    private static final DirectBuffer ANOTHER_TASK_TYPE_BUFFER = new UnsafeBuffer(ANOTHER_TASK_TYPE.getBytes(StandardCharsets.UTF_8));

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

        mockLogStream = createMockLogStream(LOG_STREAM_ID, LOG_STREAM_NAME);

        when(mockServiceBuilder.install()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockServiceContext.createService(any(), any())).thenReturn(mockServiceBuilder);
        when(mockServiceContext.removeService(any())).thenReturn(CompletableFuture.completedFuture(null));

        mockStreamProcessor = createMockStreamProcessor(TASK_TYPE_BUFFER);

        manager = new TaskSubscriptionManager(mockServiceContext, mockStreamProcessorBuilder);

        subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setTaskType(TASK_TYPE_BUFFER);
    }

    private LockTaskStreamProcessor createMockStreamProcessor(DirectBuffer taskTypeBuffer)
    {
        final LockTaskStreamProcessor mockStreamProcessor = mock(LockTaskStreamProcessor.class);

        when(mockStreamProcessorBuilder.apply(taskTypeBuffer)).thenReturn(mockStreamProcessor);
        when(mockStreamProcessor.getSubscriptedTaskType()).thenReturn(taskTypeBuffer);

        when(mockStreamProcessor.addSubscription(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockStreamProcessor.updateSubscriptionCredits(anyLong(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        return mockStreamProcessor;
    }

    private LogStream createMockLogStream(int logStreamId, String logStreamName)
    {
        final LogStream mockLogStream = mock(LogStream.class);

        final StreamContext streamContext = new StreamContext();
        streamContext.setLogName(logStreamName);

        when(mockLogStream.getContext()).thenReturn(streamContext);
        when(mockLogStream.getId()).thenReturn(logStreamId);

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
        assertThat(subscription.getId()).isEqualTo(0L);

        verify(mockStreamProcessorBuilder).apply(TASK_TYPE_BUFFER);
        verify(mockStreamProcessor).addSubscription(subscription);

        verify(mockServiceContext).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, TASK_TYPE)), any());
        verify(mockServiceBuilder).install();
    }

    @Test
    public void shouldAddSubscription() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setTaskType(TASK_TYPE_BUFFER);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(anotherSubscription.getId()).isEqualTo(1L);

        verify(mockStreamProcessorBuilder, times(1)).apply(TASK_TYPE_BUFFER);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(mockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, TASK_TYPE)), any());
        verify(mockServiceBuilder, times(1)).install();
    }

    @Test
    public void shouldCreateServiceForEachLogStream() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(ANOTHER_LOG_STREAM_ID).setTaskType(TASK_TYPE_BUFFER);

        final LogStream anotherMockLogStream = createMockLogStream(ANOTHER_LOG_STREAM_ID, ANOTHER_LOG_STREAM_NAME);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addStream(anotherMockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(anotherSubscription.getId()).isEqualTo(1L);

        verify(mockStreamProcessorBuilder, times(2)).apply(TASK_TYPE_BUFFER);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(mockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, TASK_TYPE)), any());
        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(ANOTHER_LOG_STREAM_NAME, TASK_TYPE)), any());
        verify(mockServiceBuilder, times(2)).install();
    }

    @Test
    public void shouldCreateServiceForEachTaskType() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setTaskType(ANOTHER_TASK_TYPE_BUFFER);

        final LockTaskStreamProcessor anotherMockStreamProcessor = createMockStreamProcessor(ANOTHER_TASK_TYPE_BUFFER);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompleted();
        assertThat(anotherSubscription.getId()).isEqualTo(1L);

        verify(mockStreamProcessorBuilder, times(1)).apply(TASK_TYPE_BUFFER);
        verify(mockStreamProcessorBuilder, times(1)).apply(ANOTHER_TASK_TYPE_BUFFER);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(anotherMockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, TASK_TYPE)), any());
        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, ANOTHER_TASK_TYPE)), any());
        verify(mockServiceBuilder, times(2)).install();
    }

    @Test
    public void shouldUpdateSubscriptionCredits() throws Exception
    {
        // given
        subscription.setCredits(2);

        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.updateSubscriptionCredits(subscription.setCredits(5));

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).updateSubscriptionCredits(0L, 5);
    }

    @Test
    public void shouldRemoveLastSubscriptionAndRemoveService() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).removeSubscription(0L);

        verify(mockServiceContext).removeService(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, TASK_TYPE));
    }

    @Test
    public void shouldRemoveSubscription() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(true));

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).removeSubscription(0L);

        verify(mockServiceContext, never()).removeService(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME, TASK_TYPE));
    }

    @Test
    public void shouldIgnoreRemoveSubscriptionIfProcessorNotExistForLogStream() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(ANOTHER_LOG_STREAM_ID).setTaskType(TASK_TYPE_BUFFER);

        manager.addSubscription(anotherSubscription);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

        manager.doWork();

        // then
        assertThat(future).isCompleted();
    }

    @Test
    public void shouldIgnoreRemoveSubscriptionIfProcessorNotExistForTaskType() throws Exception
    {
        // given
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setTaskType(ANOTHER_TASK_TYPE_BUFFER);

        manager.addSubscription(anotherSubscription);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

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
            .hasMessage("Topic with id '2' not found.");

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
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setTaskType(TASK_TYPE_BUFFER);

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
    public void shouldFailToUpdateSubscriptionCreditsIfProcessorNotExistForLogStream() throws Exception
    {
        // given
        subscription.setId(3L);

        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(ANOTHER_LOG_STREAM_ID).setTaskType(TASK_TYPE_BUFFER);
        manager.addSubscription(anotherSubscription);

        // when
        final CompletableFuture<Void> future = manager.updateSubscriptionCredits(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Subscription with id '3' not found.");

        verify(mockStreamProcessor, never()).updateSubscriptionCredits(anyLong(), anyInt());
    }

    @Test
    public void shouldFailToUpdateSubscriptionCreditsIfProcessorNotExistForTaskType() throws Exception
    {
        // given
        subscription.setId(3L);

        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setTaskType(ANOTHER_TASK_TYPE_BUFFER);
        manager.addSubscription(anotherSubscription);

        // when
        final CompletableFuture<Void> future = manager.updateSubscriptionCredits(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Subscription with id '3' not found.");

        verify(mockStreamProcessor, never()).updateSubscriptionCredits(anyLong(), anyInt());
    }

    @Test
    public void shouldFailToUpdateSubscriptionCreditsIfLogStreamIsRemoved() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        manager.removeStream(mockLogStream);

        // when
        final CompletableFuture<Void> future = manager.updateSubscriptionCredits(subscription.setCredits(5));
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Subscription with id '0' not found.");

        verify(mockStreamProcessor, never()).updateSubscriptionCredits(anyLong(), anyInt());
    }

    @Test
    public void shouldPropagateFailureWhileUpdateSubscriptionCredits() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockStreamProcessor.updateSubscriptionCredits(anyLong(), anyInt())).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        // when
        final CompletableFuture<Void> future = manager.updateSubscriptionCredits(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("foo");
    }

    @Test
    public void shouldPropagateFailureWhileRemoveSubscription() throws Exception
    {
        // given
        manager.addStream(mockLogStream, LOG_STREAM_SERVICE_NAME);
        manager.addSubscription(subscription);

        when(mockServiceContext.removeService(any())).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);
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
