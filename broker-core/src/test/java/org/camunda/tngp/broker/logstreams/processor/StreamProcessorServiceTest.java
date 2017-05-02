package org.camunda.tngp.broker.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_TOPIC_NAME_BUFFER;
import static org.mockito.Mockito.*;

import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.logstreams.log.*;
import org.camunda.tngp.logstreams.processor.*;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class StreamProcessorServiceTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public MockStreamProcessorController<TestEvent> mockController = new MockStreamProcessorController<>(
        TestEvent.class);

    @Test
    public void shouldRegisterVersionFilter()
    {
        // given
        final StreamProcessorService streamProcessorService = new StreamProcessorService("foo", 1, mock(StreamProcessor.class));
        injectMocks(streamProcessorService);

        streamProcessorService.start(mock(ServiceStartContext.class));

        final StreamProcessorController controller = streamProcessorService.getStreamProcessorController();
        final EventFilter eventFilter = controller.getEventFilter();

        final LoggedEvent event = mockController.buildLoggedEvent(1L, (e) ->
        { }, (m) -> m.protocolVersion(Integer.MAX_VALUE));

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot handle event with version newer than what is implemented by broker");

        // when
        eventFilter.applies(event);
    }

    @Test
    public void shouldRegisterCustomRejectingFilter()
    {
        // given
        final StreamProcessorService streamProcessorService = new StreamProcessorService("foo", 1, mock(StreamProcessor.class));
        injectMocks(streamProcessorService);
        streamProcessorService.eventFilter((m) -> false);

        streamProcessorService.start(mock(ServiceStartContext.class));

        final StreamProcessorController controller = streamProcessorService.getStreamProcessorController();
        final EventFilter eventFilter = controller.getEventFilter();

        final LoggedEvent event = mockController.buildLoggedEvent(1L, (e) ->
        { });

        // when/then
        assertThat(eventFilter.applies(event)).isFalse();
    }

    @Test
    public void shouldRegisterCustomAcceptingFilter()
    {
        // given
        final StreamProcessorService streamProcessorService = new StreamProcessorService("foo", 1, mock(StreamProcessor.class));
        injectMocks(streamProcessorService);
        streamProcessorService.eventFilter((m) -> true);

        streamProcessorService.start(mock(ServiceStartContext.class));

        final StreamProcessorController controller = streamProcessorService.getStreamProcessorController();
        final EventFilter eventFilter = controller.getEventFilter();

        final LoggedEvent event = mockController.buildLoggedEvent(1L, (e) ->
        { });

        // when/then
        assertThat(eventFilter.applies(event)).isTrue();
    }

    @Test
    public void shouldRegisterReprocessingVersionFilter()
    {
        // given
        final StreamProcessorService streamProcessorService = new StreamProcessorService("foo", 1, mock(StreamProcessor.class));
        injectMocks(streamProcessorService);

        streamProcessorService.start(mock(ServiceStartContext.class));

        final StreamProcessorController controller = streamProcessorService.getStreamProcessorController();
        final EventFilter reprocessingEventFilter = controller.getReprocessingEventFilter();

        final LoggedEvent event = mockController.buildLoggedEvent(1L, (e) ->
        { }, (m) -> m.protocolVersion(Integer.MAX_VALUE));

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot handle event with version newer than what is implemented by broker");

        // when
        reprocessingEventFilter.applies(event);
    }

    @Test
    public void shouldRegisterCustomReprocessingAcceptingFilter()
    {
        // given
        final StreamProcessorService streamProcessorService = new StreamProcessorService("foo", 1, mock(StreamProcessor.class));
        injectMocks(streamProcessorService);
        streamProcessorService.reprocessingEventFilter(e -> true);

        streamProcessorService.start(mock(ServiceStartContext.class));

        final StreamProcessorController controller = streamProcessorService.getStreamProcessorController();
        final EventFilter reprocessingEventFilter = controller.getReprocessingEventFilter();

        final LoggedEvent event = mockController.buildLoggedEvent(1L, (e) ->
        { });

        // when/then
        assertThat(reprocessingEventFilter.applies(event)).isTrue();
    }

    @Test
    public void shouldRegisterCustomReprocessingRejectingFilter()
    {
        // given
        final StreamProcessorService streamProcessorService = new StreamProcessorService("foo", 1, mock(StreamProcessor.class));
        injectMocks(streamProcessorService);
        streamProcessorService.reprocessingEventFilter(e -> false);

        streamProcessorService.start(mock(ServiceStartContext.class));

        final StreamProcessorController controller = streamProcessorService.getStreamProcessorController();
        final EventFilter reprocessingEventFilter = controller.getReprocessingEventFilter();

        final LoggedEvent event = mockController.buildLoggedEvent(1L, (e) ->
        { });

        // when/then
        assertThat(reprocessingEventFilter.applies(event)).isFalse();
    }

    protected void injectMocks(StreamProcessorService streamProcessorService)
    {
        final AgentRunnerServices agentRunnerServices = mock(AgentRunnerServices.class);
        final AgentRunnerService agentRunnerService = mock(AgentRunnerService.class);
        when(agentRunnerServices.logStreamProcessorAgentRunnerService()).thenReturn(agentRunnerService);
        streamProcessorService.getAgentRunnerInjector().inject(agentRunnerServices);

        final LogStream logStream = mock(LogStream.class);
        when(logStream.getTopicName()).thenReturn(DEFAULT_TOPIC_NAME_BUFFER);
        when(logStream.getPartitionId()).thenReturn(0);
        streamProcessorService.getSourceStreamInjector().inject(logStream);
        streamProcessorService.getTargetStreamInjector().inject(logStream);

        streamProcessorService.getSnapshotStorageInjector().inject(mock(SnapshotStorage.class));
    }

    public static class TestEvent extends UnpackedObject
    {
    }
}
