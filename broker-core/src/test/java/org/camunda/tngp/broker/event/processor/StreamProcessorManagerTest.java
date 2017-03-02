package org.camunda.tngp.broker.event.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.event.handler.FuturePool;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.test.util.FluentMock;
import org.camunda.tngp.util.DeferredCommandContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StreamProcessorManagerTest
{

    public static final ServiceName<LogStream> LOG_STREAM_NAME = ServiceName.newServiceName("foo", LogStream.class);
    public static final ServiceName<StreamProcessorController> CONTROLLER_NAME = ServiceName.newServiceName("bar", StreamProcessorController.class);

    @Mock
    protected ServiceStartContext serviceContext;
    @FluentMock
    protected ServiceBuilder serviceBuilder;
    protected FuturePool serviceInstallFutures = new FuturePool();
    protected FuturePool serviceRemoveFutures = new FuturePool();

    protected DeferredCommandContext asyncContext;

    @Mock
    protected StreamProcessor streamProcessor;

    protected ArgumentCaptor<StreamProcessorService> serviceCaptor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        asyncContext = new DeferredCommandContext();
        when(serviceContext.createService(any(), any())).thenReturn(serviceBuilder);
        when(serviceBuilder.install()).thenAnswer((invocation) -> serviceInstallFutures.next());
        when(serviceContext.removeService(any())).thenAnswer((invocation) -> serviceRemoveFutures.next());

        serviceCaptor = ArgumentCaptor.forClass(StreamProcessorService.class);
    }


    @Test
    public void shouldScheduleStreamProcessorServiceCreation()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);

        // when
        final CompletableFuture<StreamProcessorService> future =
                manager.createStreamProcessorService(LOG_STREAM_NAME, CONTROLLER_NAME, 15, streamProcessor);

        // then
        assertThat(future).isNotDone();
        verify(serviceContext).createService(same(CONTROLLER_NAME), serviceCaptor.capture());

        final StreamProcessorService service = serviceCaptor.getValue();
        verify(serviceBuilder).dependency(LOG_STREAM_NAME, service.getSourceStreamInjector());
        verify(serviceBuilder).dependency(LOG_STREAM_NAME, service.getTargetStreamInjector());
        verify(serviceBuilder).dependency(SNAPSHOT_STORAGE_SERVICE, service.getSnapshotStorageInjector());
        verify(serviceBuilder).dependency(AGENT_RUNNER_SERVICE, service.getAgentRunnerInjector());
        verify(serviceBuilder).install();
    }

    @Test
    public void shouldCreateStreamProcessorService()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);
        final CompletableFuture<StreamProcessorService> future =
                manager.createStreamProcessorService(LOG_STREAM_NAME, CONTROLLER_NAME, 15, streamProcessor);

        // when
        serviceInstallFutures.at(0).complete(null);

        // then
        assertThat(future).isCompleted();
    }

    @Test
    public void shouldScheduleStreamProcessorServiceRemoval()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);

        // when
        final CompletableFuture<Void> future = manager.removeStreamProcessorService(CONTROLLER_NAME);

        // then
        assertThat(future).isNotDone();
    }

    @Test
    public void shouldRemoveStreamProcessorService()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);
        final CompletableFuture<Void> future = manager.removeStreamProcessorService(CONTROLLER_NAME);

        // when
        serviceRemoveFutures.at(0).complete(null);

        // then
        assertThat(future).isCompleted();
    }

    @Test
    public void shouldScheduleLogStreamAddition()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);
        final LogStream stream = mock(LogStream.class);
        when(stream.getId()).thenReturn(123);

        // when
        manager.addLogStream(LOG_STREAM_NAME, stream);

        // then
        assertThat(manager.getServiceName(123)).isNull();
    }

    @Test
    public void shouldAddLogStream()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);
        final LogStream stream = mock(LogStream.class);
        when(stream.getId()).thenReturn(123);

        manager.addLogStream(LOG_STREAM_NAME, stream);

        // when
        asyncContext.doWork();

        // then
        assertThat(manager.getServiceName(123)).isEqualTo(LOG_STREAM_NAME);
    }

    @Test
    public void shouldScheduleLogStreamRemoval()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);
        final LogStream stream = mock(LogStream.class);
        when(stream.getId()).thenReturn(123);

        manager.addLogStream(LOG_STREAM_NAME, stream);
        asyncContext.doWork();

        // when
        manager.removeLogStream(stream);

        // then
        assertThat(manager.getServiceName(123)).isNotNull(); // not removed yet
    }

    @Test
    public void shouldRemoveLogStream()
    {
        // given
        final StreamProcessorManager manager = new StreamProcessorManager(serviceContext, asyncContext);
        final LogStream stream = mock(LogStream.class);
        when(stream.getId()).thenReturn(123);

        manager.addLogStream(LOG_STREAM_NAME, stream);
        asyncContext.doWork();

        manager.removeLogStream(stream);

        // when
        asyncContext.doWork();

        // then
        assertThat(manager.getServiceName(123)).isNull();
    }
}
