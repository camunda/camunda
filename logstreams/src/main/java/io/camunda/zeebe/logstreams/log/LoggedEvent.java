/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.log;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/** Represents an event on the log stream. */
public interface LoggedEvent extends BufferWriter {
  /** @return the event's position in the log. */
  long getPosition();

  /**
   * @return the position of the event which causes this event. Returns a negative value if no such
   *     an event exists.
   */
  long getSourceEventPosition();

  /** @return the key of the event */
  long getKey();

  /** @return the timestamp of the event, the ActorClock current time when the event was written */
  long getTimestamp();

  /**
   * @return a buffer containing the event's metadata at offset {@link #getMetadataOffset()} and
   *     with length {@link #getMetadataLength()}.
   */
  DirectBuffer getMetadata();

  /** @return the offset of the event's metadata */
  int getMetadataOffset();

  /** @return the length of the event's metadata */
  short getMetadataLength();

  /**
   * Wraps the given buffer to read the event's metadata
   *
   * @param reader the reader to read into
   */
  void readMetadata(BufferReader reader);

  /**
   * @return a buffer containing the value of the event at offset {@link #getValueOffset()} ()} and
   *     with length {@link #getValueLength()} ()}.
   */
  DirectBuffer getValueBuffer();

  /** @return the buffer offset where the event's value can read from */
  int getValueOffset();

  /** @return the length of the event's value */
  int getValueLength();

  /**
   * Wraps the given buffer to read the event's value.
   *
   * @param reader the buffer to read from
   */
  void readValue(BufferReader reader);
}
