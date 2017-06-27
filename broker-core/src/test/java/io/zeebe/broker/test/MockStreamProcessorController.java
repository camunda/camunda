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
package io.zeebe.broker.test;

import static io.zeebe.protocol.clientapi.EventType.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.FluentAnswer;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.junit.rules.ExternalResource;

public class MockStreamProcessorController<T extends UnpackedObject> extends ExternalResource
{
    protected LogStreamWriter mockLogStreamWriter;
    protected LogStreamReader mockSourceStreamReader;

    protected DeferredCommandContext cmdQueue;

    protected StreamProcessor streamProcessor;
    protected long position;

    protected Class<T> eventClass;
    protected Consumer<T> defaultEventSetter;
    protected Consumer<BrokerEventMetadata> defaultMetadataSetter;

    protected List<WrittenEvent<T>> writtenEvents;
    protected long lastEventKey;
    protected T lastEventValue;
    protected BrokerEventMetadata lastEventMetadata;

    public MockStreamProcessorController(Class<T> eventClass, Consumer<T> defaultEventSetter, EventType defaultEventType, long initialPosition)
    {
        this.eventClass = eventClass;
        this.writtenEvents = new ArrayList<>();
        this.defaultEventSetter = defaultEventSetter;
        this.defaultMetadataSetter = (m) ->
        {
            m.subscriberKey(0L);
            m.protocolVersion(0);
            m.raftTermId(0);
            m.requestStreamId(0);
            m.requestId(0);
            m.eventType(defaultEventType);
        };
        this.position = initialPosition;
    }


    public MockStreamProcessorController(Class<T> eventClass, EventType eventType)
    {
        this(eventClass, (t) ->
        { }, eventType, 0);
    }

    public MockStreamProcessorController(Class<T> eventClass, EventType eventType, long initialPosition)
    {
        this(eventClass, (t) ->
        { }, eventType, initialPosition);
    }

    public MockStreamProcessorController(Class<T> eventClass)
    {
        this(eventClass, NULL_VAL);
    }

    @Override
    protected void before() throws Throwable
    {
        mockLogStreamWriter = mock(LogStreamWriter.class, new FluentAnswer());
        mockSourceStreamReader = mock(LogStreamReader.class);

        doAnswer(invocation ->
        {
            final BrokerEventMetadata metadata = new BrokerEventMetadata();
            final BufferWriter writer = (BufferWriter) invocation.getArguments()[0];
            populate(writer, metadata);
            lastEventMetadata = metadata;
            return invocation.getMock();
        }).when(mockLogStreamWriter).metadataWriter(any(BufferWriter.class));

        doAnswer(invocation ->
        {
            final BufferWriter writer = (BufferWriter) invocation.getArguments()[0];
            final T event = newEventInstance();
            populate(writer, event);
            lastEventValue = event;
            return invocation.getMock();
        }).when(mockLogStreamWriter).valueWriter(any(BufferWriter.class));

        doAnswer(invocation ->
        {
            final long key = (long) invocation.getArguments()[0];
            lastEventKey = key;
            return invocation.getMock();
        }).when(mockLogStreamWriter).key(anyLong());

        doAnswer(invocation ->
        {
            final WrittenEvent<T> lastWrittenEvent = new WrittenEvent<>(lastEventKey, lastEventValue, lastEventMetadata);
            writtenEvents.add(lastWrittenEvent);

            return 1L;
        }).when(mockLogStreamWriter).tryWrite();

        cmdQueue = new DeferredCommandContext(10);
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
    }

    public void initStreamProcessor(StreamProcessor streamProcessor)
    {
        initStreamProcessor(streamProcessor, new StreamProcessorContext());
    }

    public void initStreamProcessor(StreamProcessor streamProcessor, StreamProcessorContext context)
    {
        this.streamProcessor = streamProcessor;

        context.setStreamProcessorCmdQueue(cmdQueue);
        context.setLogStreamWriter(mockLogStreamWriter);
        context.setSourceLogStreamReader(mockSourceStreamReader);

        streamProcessor.onOpen(context);
    }

    public List<WrittenEvent<T>> getWrittenEvents()
    {
        return writtenEvents;
    }

    public List<T> getWrittenEventValues()
    {
        return writtenEvents.stream().map(WrittenEvent::getValue).collect(Collectors.toList());
    }

    public WrittenEvent<T> getLastWrittenEvent()
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

    public T getLastWrittenEventValue()
    {
        if (writtenEvents.size() > 0)
        {
            return writtenEvents.get(writtenEvents.size() - 1).getValue();
        }
        else
        {
            throw new RuntimeException("There are no written events");
        }
    }

    public BrokerEventMetadata getLastWrittenEventMetadata()
    {
        if (writtenEvents.size() > 0)
        {
            return writtenEvents.get(writtenEvents.size() - 1).getMetadata();
        }
        else
        {
            throw new RuntimeException("There are no written events");
        }
    }

    public void processEvent(long key, Consumer<T> eventSetter)
    {
        processEvent(key, eventSetter, metadata ->
        { });
    }

    public void processEvent(long key, Consumer<T> eventSetter, Consumer<BrokerEventMetadata> metadataSetter)
    {
        Objects.requireNonNull(streamProcessor, "No stream processor set. Call 'initStreamProcessor()' in setup method.");

        final LoggedEvent mockLoggedEvent = buildLoggedEvent(key, eventSetter, metadataSetter);

        simulateStreamProcessorController(mockLoggedEvent);
    }

    public void processEvent(LoggedEvent event)
    {
        simulateStreamProcessorController(event);
    }

    public void drainCommandQueue()
    {
        cmdQueue.doWork();
    }

    protected void simulateStreamProcessorController(final LoggedEvent loggedEvent)
    {
        drainCommandQueue();

        if (!streamProcessor.isSuspended())
        {
            final EventProcessor eventProcessor = streamProcessor.onEvent(loggedEvent);
            if (eventProcessor != null)
            {
                eventProcessor.processEvent();
                eventProcessor.executeSideEffects();
                eventProcessor.writeEvent(mockLogStreamWriter);
                eventProcessor.updateState();
            }
        }
    }

    public LoggedEvent buildLoggedEvent(long key, Consumer<T> eventSetter)
    {
        return buildLoggedEvent(key, eventSetter, m ->
        { });
    }

    public LoggedEvent buildLoggedEvent(long key, Consumer<T> eventSetter, Consumer<BrokerEventMetadata> metadataSetter)
    {

        final LoggedEvent mockLoggedEvent = mock(LoggedEvent.class);

        when(mockLoggedEvent.getKey()).thenReturn(key);
        when(mockLoggedEvent.getPosition()).thenReturn(position);
        position++;

        final T event = newEventInstance();
        final DirectBuffer buf = populateAndWrite(event, defaultEventSetter.andThen(eventSetter));

        doAnswer(invocation ->
        {
            final BufferReader arg = (BufferReader) invocation.getArguments()[0];
            arg.wrap(buf, 0, buf.capacity());
            return null;
        }).when(mockLoggedEvent).readValue(any());

        final BrokerEventMetadata metaData = new BrokerEventMetadata();
        final DirectBuffer metaDataBuf = populateAndWrite(metaData, defaultMetadataSetter.andThen(metadataSetter));
        doAnswer(invocation ->
        {
            final BufferReader arg = (BufferReader) invocation.getArguments()[0];
            arg.wrap(metaDataBuf, 0, metaDataBuf.capacity());
            return null;
        }).when(mockLoggedEvent).readMetadata(any());

        return mockLoggedEvent;
    }

    protected <S extends BufferWriter> DirectBuffer populateAndWrite(S writer, Consumer<S> setter)
    {
        setter.accept(writer);
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[writer.getLength()]);
        writer.write(buf, 0);
        return buf;
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
