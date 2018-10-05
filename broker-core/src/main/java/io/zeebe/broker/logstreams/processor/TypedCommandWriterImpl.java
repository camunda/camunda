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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriterImpl;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
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

  protected LogStreamRecordWriter writer;
  protected LogStreamBatchWriter batchWriter;

  protected int producerId;
  protected long sourceRecordPosition = -1;

  protected LogStreamWriter stagedWriter;

  public TypedCommandWriterImpl(
      LogStream stream, Map<ValueType, Class<? extends UnpackedObject>> eventRegistry) {
    this.stream = stream;
    metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
    this.writer = new LogStreamWriterImpl(stream);
    this.batchWriter = new LogStreamBatchWriterImpl(stream);
    this.typeRegistry = new HashMap<>();
    eventRegistry.forEach((e, c) -> typeRegistry.put(c, e));
  }

  public void configureSourceContext(int producerId, long sourceRecordPosition) {
    this.producerId = producerId;
    this.sourceRecordPosition = sourceRecordPosition;
  }

  protected void initMetadata(RecordType type, Intent intent, UnpackedObject value) {
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

  protected void writeRecord(
      long key,
      RecordType type,
      Intent intent,
      UnpackedObject value,
      Consumer<RecordMetadata> additionalMetadata) {
    writeRecord(key, type, intent, RejectionType.NULL_VAL, "", value, additionalMetadata);
  }

  protected void writeRecord(
      long key,
      RecordType type,
      Intent intent,
      RejectionType rejectionType,
      String rejectionReason,
      UnpackedObject value,
      Consumer<RecordMetadata> additionalMetadata) {

    stagedWriter = writer;

    writer.reset();
    writer.producerId(producerId);

    if (sourceRecordPosition >= 0) {
      writer.sourceRecordPosition(sourceRecordPosition);
    }

    initMetadata(type, intent, value);
    metadata.rejectionType(rejectionType);
    metadata.rejectionReason(rejectionReason);
    additionalMetadata.accept(metadata);

    if (key >= 0) {
      writer.key(key);
    } else {
      writer.keyNull();
    }

    writer.metadataWriter(metadata).valueWriter(value);
  }

  @Override
  public void writeNewCommand(Intent intent, UnpackedObject value) {
    writeRecord(-1, RecordType.COMMAND, intent, value, noop);
  }

  @Override
  public void writeFollowUpCommand(long key, Intent intent, UnpackedObject value) {
    writeRecord(key, RecordType.COMMAND, intent, value, noop);
  }

  @Override
  public void writeFollowUpCommand(
      long key, Intent intent, UnpackedObject value, Consumer<RecordMetadata> metadata) {
    writeRecord(key, RecordType.COMMAND, intent, value, metadata);
  }

  public void reset() {
    stagedWriter = null;
  }

  @Override
  public long flush() {
    if (stagedWriter != null) {
      return stagedWriter.tryWrite();
    } else {
      return 0L;
    }
  }
}
