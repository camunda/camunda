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

import java.util.stream.Stream;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.test.util.stream.StreamWrapper;

public class TypedRecordStream<T extends UnpackedObject> extends StreamWrapper<TypedRecord<T>>
{

    public TypedRecordStream(Stream<TypedRecord<T>> wrappedStream)
    {
        super(wrappedStream);
    }

    public TypedRecordStream<T> onlyCommands()
    {
        return new TypedRecordStream<>(filter(r -> r.getMetadata().getRecordType() == RecordType.COMMAND));
    }

    public TypedRecordStream<T> onlyEvents()
    {
        return new TypedRecordStream<>(filter(r -> r.getMetadata().getRecordType() == RecordType.EVENT));
    }

    public TypedRecordStream<T> onlyRejections()
    {
        return new TypedRecordStream<>(filter(r -> r.getMetadata().getRecordType() == RecordType.COMMAND_REJECTION));
    }

    public TypedRecordStream<T> withIntent(Intent intent)
    {
        return new TypedRecordStream<>(filter(r -> r.getMetadata().getIntent() == intent));
    }

}
