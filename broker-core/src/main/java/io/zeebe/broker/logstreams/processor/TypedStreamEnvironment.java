/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import java.util.EnumMap;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.transport.ServerOutput;

public class TypedStreamEnvironment
{

    protected final ServerOutput output;
    protected final LogStream stream;
    protected final EnumMap<EventType, Class<? extends UnpackedObject>> eventRegistry = new EnumMap<>(EventType.class);

    public TypedStreamEnvironment(LogStream stream, ServerOutput output)
    {
        this.output = output;
        this.stream = stream;
    }

    public TypedStreamEnvironment withEventType(EventType type, Class<? extends UnpackedObject> pojoClass)
    {
        eventRegistry.put(type, pojoClass);
        return this;
    }

    public EnumMap<EventType, Class<? extends UnpackedObject>> getEventRegistry()
    {
        return eventRegistry;
    }

    public ServerOutput getOutput()
    {
        return output;
    }

    public TypedEventStreamProcessorBuilder newStreamProcessor()
    {
        return new TypedEventStreamProcessorBuilder(this);
    }

    public TypedStreamWriter buildStreamWriter()
    {
        return new TypedStreamWriterImpl(stream, eventRegistry);
    }

    public MetadataFilter buildFilterForRegisteredTypes()
    {
        return m -> eventRegistry.containsKey(m.getEventType());
    }

    public TypedStreamReader buildStreamReader()
    {
        return new TypedStreamReaderImpl(stream, eventRegistry);
    }
}
