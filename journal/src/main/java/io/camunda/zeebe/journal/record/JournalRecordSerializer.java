/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import io.camunda.zeebe.util.Either;
import java.nio.BufferOverflowException;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface JournalRecordSerializer {

  /**
   * Writes a {@link RecordData} to the buffer.
   *
   * @param record to write
   * @param buffer to which the record will be written
   * @param offset the offset in the buffer at which the data will be written
   * @return Either an error if there is not enough space or the number of bytes that were written
   *     to the buffer
   */
  Either<BufferOverflowException, Integer> writeData(
      RecordData record, MutableDirectBuffer buffer, int offset);

  /**
   * Writes a {@link RecordMetadata} to the buffer.
   *
   * @param metadata to write
   * @param buffer to which the metadata will be written
   * @param offset the offset in the buffer at which the metadata will be written
   * @return the number of bytes that were written to the buffer
   */
  int writeMetadata(RecordMetadata metadata, MutableDirectBuffer buffer, int offset);

  /**
   * Returns the number of bytes required to write a {@link RecordMetadata} to a buffer. The length
   * returned by this method must be equal to the length returned by {@link
   * JournalRecordSerializer#writeMetadata(RecordMetadata, MutableDirectBuffer, int)}
   *
   * @return the expected length of a serialized metadata
   */
  int getMetadataLength();

  /**
   * Reads the {@link RecordMetadata} from the buffer at offset 0. A valid record must exist in the
   * buffer at this position.
   *
   * @param buffer to read
   * @param offset the offset in the buffer at which the metadata will be read from
   * @return a journal record metadata that is read.
   */
  RecordMetadata readMetadata(DirectBuffer buffer, int offset);

  /**
   * Reads the {@link RecordData} from the buffer at offset 0. A valid record must exist in the
   * buffer at this position.
   *
   * @param buffer to read
   * @param offset the offset in the buffer at which the data will be read from
   * @return a journal indexed record that is read.
   */
  RecordData readData(DirectBuffer buffer, int offset, int length);

  /**
   * Returns the length of the serialized {@link RecordMetadata} in the buffer.
   *
   * @param buffer to read
   * @param offset the offset in the buffer at which the metadata will be read from
   * @return the length of the metadata
   */
  int getMetadataLength(DirectBuffer buffer, int offset);
}
