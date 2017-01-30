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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorCommand;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.rules.ExternalResource;

public class MockStreamProcessorController<T extends UnpackedObject> extends ExternalResource
{
    protected LoggedEvent mockLoggedEvent;
    protected LogStreamWriter mockLogStreamWriter;

    protected ManyToOneConcurrentArrayQueue<StreamProcessorCommand> cmdQueue;

    protected StreamProcessor streamProcessor;

    protected Class<T> eventClass;
    protected Consumer<T> defaultEventSetter;
    protected List<T> writtenEvents;
    protected List<BrokerEventMetadata> writtenMetadata;

    public MockStreamProcessorController(Class<T> eventClass, Consumer<T> defaultEventSetter)
    {
        this.eventClass = eventClass;
        this.writtenEvents = new ArrayList<>();
        this.writtenMetadata = new ArrayList<>();
        this.defaultEventSetter = defaultEventSetter;
    }


    public MockStreamProcessorController(Class<T> eventClass)
    {
        this(eventClass, (t) ->
        { });
    }

    @Override
    protected void before() throws Throwable
    {
        mockLoggedEvent = mock(LoggedEvent.class);

        mockLogStreamWriter = mock(LogStreamWriter.class, new FluentAnswer());
        when(mockLogStreamWriter.tryWrite()).thenReturn(1L);

        doAnswer(invocation ->
        {
            final BrokerEventMetadata metadata = new BrokerEventMetadata();
            final BufferWriter writer = (BufferWriter) invocation.getArguments()[0];
            populate(writer, metadata);
            writtenMetadata.add(metadata);
            return invocation.getMock();
        }).when(mockLogStreamWriter).metadataWriter(any(BufferWriter.class));

        doAnswer(invocation ->
        {
            final BufferWriter writer = (BufferWriter) invocation.getArguments()[0];
            final T event = newEventInstance();
            populate(writer, event);
            writtenEvents.add(event);
            return invocation.getMock();
        }).when(mockLogStreamWriter).valueWriter(any(BufferWriter.class));

        cmdQueue = new ManyToOneConcurrentArrayQueue<>(10);
    }

    protected void populate(BufferWriter writer, BufferReader reader)
    {
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[writer.getLength()]);

        writer.write(buf, 0);
        reader.wrap(buf, 0, buf.capacity());
    }

    @Override
    protected void after()
    {
        writtenEvents.clear();
        writtenMetadata.clear();
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

    public List<T> getWrittenEvents()
    {
        return writtenEvents;
    }

    public T getLastWrittenEvent()
    {
        if (writtenEvents.size() > 0)
        {
            return writtenEvents.get(writtenEvents.size() - 1);
        }
        else
        {
            throw new RuntimeException("There are no written events");
        }
    }

    public BrokerEventMetadata getLastWrittenMetadata()
    {
        if (writtenMetadata.size() > 0)
        {
            return writtenMetadata.get(writtenMetadata.size() - 1);
        }
        else
        {
            throw new RuntimeException("There is no written metadata");
        }
    }

    public void processEvent(long key, Consumer<T> eventSetter)
    {
        Objects.requireNonNull(streamProcessor, "No stream processor set. Call 'initStreamProcessor()' in setup method.");

        when(mockLoggedEvent.getLongKey()).thenReturn(key);

        final T event = newEventInstance();
        defaultEventSetter.accept(event);
        eventSetter.accept(event);
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[event.getLength()]);
        event.write(buf, 0);

        doAnswer(invocation ->
        {
            final BufferReader arg = (BufferReader) invocation.getArguments()[0];
            arg.wrap(buf, 0, buf.capacity());
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

    protected T newEventInstance()
    {
        try
        {
            return eventClass.newInstance();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
