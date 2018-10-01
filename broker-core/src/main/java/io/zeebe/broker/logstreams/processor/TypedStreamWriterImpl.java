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
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import java.util.Map;
import java.util.function.Consumer;

public class TypedStreamWriterImpl extends TypedCommandWriterImpl
    implements TypedStreamWriter, TypedBatchWriter {

  private final KeyGenerator keyGenerator;

  public TypedStreamWriterImpl(
      LogStream stream,
      Map<ValueType, Class<? extends UnpackedObject>> eventRegistry,
      KeyGenerator keyGenerator) {
    super(stream, eventRegistry);
    this.keyGenerator = keyGenerator;
  }

  @Override
  public long writeNewEvent(Intent intent, UnpackedObject value) {
    final long key = keyGenerator.nextKey();
    writeRecord(key, RecordType.EVENT, intent, value, noop);
    return key;
  }

  @Override
  public void writeFollowUpEvent(long key, Intent intent, UnpackedObject value) {
    writeRecord(key, RecordType.EVENT, intent, value, noop);
  }

  @Override
  public void writeFollowUpEvent(
      long key, Intent intent, UnpackedObject value, Consumer<RecordMetadata> metadata) {
    writeRecord(key, RecordType.EVENT, intent, value, metadata);
  }

  @Override
  public void writeRejection(
      TypedRecord<? extends UnpackedObject> command, RejectionType rejectionType, String reason) {
    writeRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getMetadata().getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        noop);
  }

  @Override
  public void writeRejection(
      TypedRecord<? extends UnpackedObject> command,
      RejectionType rejectionType,
      String reason,
      Consumer<RecordMetadata> metadata) {
    writeRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getMetadata().getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        metadata);
  }

  @Override
  public void addNewCommand(Intent intent, UnpackedObject value) {
    addRecord(-1, RecordType.COMMAND, intent, value, noop);
  }

  @Override
  public void addFollowUpCommand(long key, Intent intent, UnpackedObject value) {
    addRecord(key, RecordType.COMMAND, intent, value, noop);
  }

  @Override
  public long addNewEvent(Intent intent, UnpackedObject value) {
    final long key = keyGenerator.nextKey();
    addRecord(key, RecordType.EVENT, intent, value, noop);
    return key;
  }

  @Override
  public void addFollowUpEvent(long key, Intent intent, UnpackedObject value) {
    addRecord(key, RecordType.EVENT, intent, value, noop);
  }

  @Override
  public void addFollowUpEvent(
      long key, Intent intent, UnpackedObject value, Consumer<RecordMetadata> metadata) {
    addRecord(key, RecordType.EVENT, intent, value, metadata);
  }

  private void addRecord(
      long key,
      RecordType type,
      Intent intent,
      UnpackedObject value,
      Consumer<RecordMetadata> additionalMetadata) {
    initMetadata(type, intent, value);
    additionalMetadata.accept(metadata);

    final LogEntryBuilder logEntryBuilder = batchWriter.event();

    if (key >= 0) {
      logEntryBuilder.key(key);
    } else {
      logEntryBuilder.positionAsKey();
    }

    logEntryBuilder.metadataWriter(metadata).valueWriter(value).done();
  }

  @Override
  public TypedBatchWriter newBatch() {

    batchWriter.reset();
    batchWriter.producerId(producerId);

    if (sourceRecordPosition >= 0) {
      batchWriter.sourceRecordPosition(sourceRecordPosition);
    }

    stagedWriter = batchWriter;

    return this;
  }

  @Override
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }
}
