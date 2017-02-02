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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.taskqueue.TaskSubscriptionManager;
import org.camunda.tngp.broker.taskqueue.processor.LockTaskStreamProcessor;
import org.camunda.tngp.broker.taskqueue.processor.TaskSubscription;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.StreamContext;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionManagerTest
{
    private static final int LOG_STREAM_ID = 2;
    private static final String LOG_STREAM_NAME = "task-test-log";

    @FluentMock
    private ServiceStartContext mockServiceContext;

    @FluentMock
    private ServiceBuilder<Object> mockServiceBuilder;

    @Mock
    private IdGenerator mockSubscriptionIdGenerator;

    @Mock
    private LockTaskStreamProcessor mockStreamProcessor;

    @Mock
    private LogStream mockLogStream;

    private TaskSubscriptionManager manager;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);

        final StreamContext streamContext = new StreamContext();
        streamContext.setLogName(LOG_STREAM_NAME);

        when(mockLogStream.getContext()).thenReturn(streamContext);
        when(mockLogStream.getId()).thenReturn(LOG_STREAM_ID);

        when(mockServiceBuilder.install()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockServiceContext.createService(any(), any())).thenReturn(mockServiceBuilder);
        when(mockServiceContext.removeService(any())).thenReturn(CompletableFuture.completedFuture(null));

        when(mockSubscriptionIdGenerator.nextId()).thenReturn(0L, 1L, 2L);

        when(mockStreamProcessor.addSubscription(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockStreamProcessor.updateSubscriptionCredits(anyLong(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        manager = new TaskSubscriptionManager(mockServiceContext, mockSubscriptionIdGenerator, () -> mockStreamProcessor);
    }

    @Test
    public void shouldCreateServiceAndAddSubscription() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        manager.addStream(mockLogStream);

        // when
        final CompletableFuture<Long> future = manager.addSubscription(subscription);
        manager.doWork();

        // then
        assertThat(future).isCompletedWithValue(0L);

        verify(mockStreamProcessor).addSubscription(subscription);

        verify(mockServiceContext).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME)), any());
        verify(mockServiceBuilder).install();
    }

    @Test
    public void shouldAddSubscription() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        manager.addStream(mockLogStream);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Long> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).isCompletedWithValue(1L);

        verify(mockStreamProcessor).addSubscription(subscription);
        verify(mockStreamProcessor).addSubscription(anotherSubscription);

        verify(mockServiceContext, times(1)).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME)), any());
        verify(mockServiceBuilder, times(1)).install();
    }

    @Test
    public void shouldUpdateSubscriptionCredits() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription()
                .setTopicId(LOG_STREAM_ID)
                .setCredits(2);

        manager.addStream(mockLogStream);
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
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        manager.addStream(mockLogStream);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).removeSubscription(0L);

        verify(mockServiceContext).removeService(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME));
    }

    @Test
    public void shouldRemoveSubscription() throws Exception
    {
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(true));

        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        manager.addStream(mockLogStream);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

        manager.doWork();

        // then
        assertThat(future).isCompleted();

        verify(mockStreamProcessor).removeSubscription(0L);

        verify(mockServiceContext, never()).removeService(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME));
    }

    @Test
    public void shouldIgnoreRemoveSubscriptionIfProcessorNotExist() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription);

        manager.doWork();

        // then
        assertThat(future).isCompleted();
    }

    @Test
    public void shouldFailToAddSubscriptionIfTopicNotExist() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        // when
        final CompletableFuture<Long> future = manager.addSubscription(subscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Topic with id '2' not found.");

        verify(mockStreamProcessor, never()).addSubscription(subscription);

        verify(mockServiceContext, never()).createService(eq(taskQueueLockStreamProcessorServiceName(LOG_STREAM_NAME)), any());
        verify(mockServiceBuilder, never()).install();
    }

    @Test
    public void shouldPropagateFailureWhileAddSubscriptionAfterCreateService() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        when(mockStreamProcessor.addSubscription(subscription)).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream);

        // when
        final CompletableFuture<Long> future = manager.addSubscription(subscription);
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
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);
        final TaskSubscription anotherSubscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        when(mockStreamProcessor.addSubscription(anotherSubscription)).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream);
        manager.addSubscription(subscription);

        // when
        final CompletableFuture<Long> future = manager.addSubscription(anotherSubscription);
        manager.doWork();

        // then
        assertThat(future).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("foo");
    }

    @Test
    public void shouldFailToUpdateSubscriptionCreditsIfProcessorNotExist() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription().setId(3L).setTopicId(LOG_STREAM_ID);

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
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID).setCredits(2);

        manager.addStream(mockLogStream);
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
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        when(mockStreamProcessor.updateSubscriptionCredits(anyLong(), anyInt())).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream);
        manager.addSubscription(subscription);

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
        when(mockStreamProcessor.removeSubscription(anyLong())).thenReturn(CompletableFuture.completedFuture(false));

        // given
        final TaskSubscription subscription = new TaskSubscription().setTopicId(LOG_STREAM_ID);

        when(mockServiceContext.removeService(any())).thenReturn(completedExceptionallyFuture(new RuntimeException("foo")));

        manager.addStream(mockLogStream);
        manager.addSubscription(subscription);

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
