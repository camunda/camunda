/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public interface LogStreamRecordWriter extends LogStreamWriter {

  LogStreamRecordWriter keyNull();

  LogStreamRecordWriter key(long key);

  LogStreamRecordWriter sourceRecordPosition(long position);

  LogStreamRecordWriter metadata(DirectBuffer buffer, int offset, int length);

  LogStreamRecordWriter metadata(DirectBuffer buffer);

  LogStreamRecordWriter metadataWriter(BufferWriter writer);

  LogStreamRecordWriter value(DirectBuffer value, int valueOffset, int valueLength);

  LogStreamRecordWriter value(DirectBuffer value);

  LogStreamRecordWriter valueWriter(BufferWriter writer);

  void reset();
}
