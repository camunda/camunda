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
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.logstreams.log.LogStreamBatchWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TypedCommandWriterImpl implements TypedCommandWriter {
  protected final Consumer<RecordMetadata> noop = m -> {};

  protected RecordMetadata metadata = new RecordMetadata();
  protected final Map<Class<? extends UnpackedObject>, ValueType> typeRegistry;
  protected final LogStream stream;

  protected LogStreamBatchWriter batchWriter;

  protected int producerId;
  protected long sourceRecordPosition = -1;

  public TypedCommandWriterImpl(
      final LogStream stream, final Map<ValueType, Class<? extends UnpackedObject>> eventRegistry) {
    this.stream = stream;
    metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
    this.batchWriter = new LogStreamBatchWriterImpl(stream);
    this.typeRegistry = new HashMap<>();
    eventRegistry.forEach((e, c) -> typeRegistry.put(c, e));
  }

  public void configureSourceContext(final int producerId, final long sourceRecordPosition) {
    this.producerId = producerId;
    this.sourceRecordPosition = sourceRecordPosition;
  }

  protected void initMetadata(
      final RecordType type, final Intent intent, final UnpackedObject value) {
    metadata.reset();
    final ValueType valueType = typeRegistry.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new RuntimeException("Missing value type mapping for record: " + value.getClass());
    }

    metadata.recordType(type);
    metadata.valueType(valueType);
    metadata.intent(intent);
  }

  protected void appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final UnpackedObject value,
      final Consumer<RecordMetadata> additionalMetadata) {
    appendRecord(key, type, intent, RejectionType.NULL_VAL, "", value, additionalMetadata);
  }

  protected void appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final UnpackedObject value,
      final Consumer<RecordMetadata> additionalMetadata) {
    final LogEntryBuilder event = batchWriter.event();
    batchWriter.producerId(producerId);

    if (sourceRecordPosition >= 0) {
      batchWriter.sourceRecordPosition(sourceRecordPosition);
    }

    initMetadata(type, intent, value);
    metadata.rejectionType(rejectionType);
    metadata.rejectionReason(rejectionReason);
    additionalMetadata.accept(metadata);

    if (key >= 0) {
      event.key(key);
    } else {
      event.keyNull();
    }

    event.metadataWriter(metadata).valueWriter(value).done();
  }

  @Override
  public void appendNewCommand(final Intent intent, final UnpackedObject value) {
    appendRecord(-1, RecordType.COMMAND, intent, value, noop);
  }

  @Override
  public void appendFollowUpCommand(
      final long key, final Intent intent, final UnpackedObject value) {
    appendRecord(key, RecordType.COMMAND, intent, value, noop);
  }

  @Override
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final Consumer<RecordMetadata> metadata) {
    appendRecord(key, RecordType.COMMAND, intent, value, metadata);
  }

  @Override
  public void reset() {
    batchWriter.reset();
  }

  @Override
  public long flush() {
    return batchWriter.tryWrite();
  }
}
