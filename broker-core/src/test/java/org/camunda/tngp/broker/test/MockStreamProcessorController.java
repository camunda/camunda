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
package org.camunda.tngp.broker.test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Objects;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorCommand;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.rules.ExternalResource;

public class MockStreamProcessorController<T extends UnpackedObject> extends ExternalResource
{
    protected LoggedEvent mockLoggedEvent;
    protected LogStreamWriter mockLogStreamWriter;

    protected ManyToOneConcurrentArrayQueue<StreamProcessorCommand> cmdQueue;

    protected StreamProcessor streamProcessor;

    protected T event;
    protected BrokerEventMetadata eventMetadata;

    @Override
    protected void before() throws Throwable
    {
        mockLoggedEvent = mock(LoggedEvent.class);

        mockLogStreamWriter = mock(LogStreamWriter.class, new FluentAnswer());
        when(mockLogStreamWriter.tryWrite()).thenReturn(1L);

        doAnswer(invocation ->
        {
            eventMetadata = (BrokerEventMetadata) invocation.getArguments()[0];

            return invocation.getMock();
        }).when(mockLogStreamWriter).metadataWriter(any(BufferWriter.class));

        cmdQueue = new ManyToOneConcurrentArrayQueue<>(10);
    }

    @Override
    protected void after()
    {
        event = null;
        eventMetadata = null;
    }

    public void initStreamProcessor(StreamProcessor streamProcessor)
    {
        initStreamProcessor(streamProcessor, new StreamProcessorContext());
    }

    public void initStreamProcessor(StreamProcessor streamProcessor, StreamProcessorContext context)
    {
        this.streamProcessor = streamProcessor;

        context.setStreamProcessorCmdQueue(cmdQueue);

        streamProcessor.onOpen(context);
    }

    public T getEvent()
    {
        return event;
    }

    public BrokerEventMetadata getEventMetadata()
    {
        return eventMetadata;
    }

    @SuppressWarnings("unchecked")
    public void processEvent(long key, Consumer<T> eventValueBuilder)
    {
        Objects.requireNonNull(streamProcessor, "No stream processor set. Call 'initStreamProcessor()' in setup method.");

        when(mockLoggedEvent.getLongKey()).thenReturn(key);

        doAnswer(invocation ->
        {
            event = (T) invocation.getArguments()[0];
            event.reset();

            eventValueBuilder.accept(event);
            return null;
        }).when(mockLoggedEvent).readValue(any());

        // simulate stream processor controller behavior
        cmdQueue.drain(cmd -> cmd.execute());
        final EventProcessor eventProcessor = streamProcessor.onEvent(mockLoggedEvent);
        if (eventProcessor != null)
        {
            eventProcessor.processEvent();
            eventProcessor.executeSideEffects();
            eventProcessor.writeEvent(mockLogStreamWriter);
            eventProcessor.updateState();
        }
    }

}
