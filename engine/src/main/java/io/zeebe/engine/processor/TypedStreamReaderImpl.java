/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.util.ReflectUtil;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TypedStreamReaderImpl implements TypedStreamReader {
  protected final LogStreamReader reader;
  protected final TypedEventImpl event = new TypedEventImpl();
  protected final RecordMetadata metadata = new RecordMetadata();
  protected final Map<Class<? extends UnpackedObject>, UnpackedObject> eventCache;

  public TypedStreamReaderImpl(
      LogStream stream, EnumMap<ValueType, Class<? extends UnpackedObject>> eventRegistry) {
    this.reader = new BufferedLogStreamReader(stream);
    this.eventCache = new HashMap<>();
    eventRegistry.forEach((t, c) -> eventCache.put(c, ReflectUtil.newInstance(c)));
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T extends UnpackedObject> TypedRecord<T> readValue(long position, Class<T> eventClass) {
    final boolean success = reader.seek(position);
    if (!success) {
      throw new RuntimeException("Could not find an event at position " + position);
    }

    final LoggedEvent rawEvent = reader.next();
    metadata.reset();
    rawEvent.readMetadata(metadata);

    final UnpackedObject value = eventCache.get(eventClass);
    value.reset();
    rawEvent.readValue(value);

    event.wrap(rawEvent, metadata, value);

    return event;
  }

  @Override
  public void close() {
    reader.close();
  }
}
