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
package io.zeebe.broker.util;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedEventImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.util.ReflectUtil;

public class CopiedTypedEvent extends TypedEventImpl
{
    private final long key;
    private final long position;
    private final long sourcePosition;
    private final RecordMetadata metadata;

    CopiedTypedEvent(LoggedEvent event, UnpackedObject object)
    {
        this.value = object;
        this.position = event.getPosition();
        this.sourcePosition = event.getSourceEventPosition();
        this.key = event.getKey();
        this.metadata = new RecordMetadata();
        event.readMetadata(metadata);
    }

    @Override
    public long getKey()
    {
        return key;
    }

    @Override
    public long getSourcePosition()
    {
        return sourcePosition;
    }

    @Override
    public long getPosition()
    {
        return position;
    }

    @Override
    public RecordMetadata getMetadata()
    {
        return metadata;
    }

    public static <T extends UnpackedObject> TypedRecord<T> toTypedEvent(LoggedEvent event, Class<T> valueClass)
    {
        final T value = ReflectUtil.newInstance(valueClass);
        value.wrap(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
        return new CopiedTypedEvent(event, value);
    }
}
