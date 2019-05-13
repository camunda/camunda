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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import java.util.Map;
import java.util.function.Consumer;

public class TypedStreamWriterImpl extends TypedCommandWriterImpl implements TypedStreamWriter {

  public TypedStreamWriterImpl(
      final LogStream stream, final Map<ValueType, Class<? extends UnpackedObject>> eventRegistry) {
    super(stream, eventRegistry);
  }

  @Override
  public void appendNewEvent(final long key, final Intent intent, final UnpackedObject value) {
    appendRecord(key, RecordType.EVENT, intent, value, noop);
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final UnpackedObject value) {
    appendRecord(key, RecordType.EVENT, intent, value, noop);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final Consumer<RecordMetadata> metadata) {
    appendRecord(key, RecordType.EVENT, intent, value, metadata);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType rejectionType,
      final String reason) {
    appendRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getMetadata().getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        noop);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType rejectionType,
      final String reason,
      final Consumer<RecordMetadata> metadata) {
    appendRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getMetadata().getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        metadata);
  }
}
