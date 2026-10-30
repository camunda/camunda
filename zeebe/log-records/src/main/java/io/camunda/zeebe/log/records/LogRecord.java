/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.log.records;

import io.camunda.zeebe.protocol.record.Record;

/**
 * One record of a partition's log. A reader that is older than the partition's broker can meet
 * value types it doesn't know; it gets those as {@link Unknown} instead of failing, so it can skip
 * and report them.
 */
public sealed interface LogRecord {

  /** The record's position in the partition's log. */
  long position();

  /** A record whose value this reader can decode. */
  record Known(Record<?> record) implements LogRecord {

    @Override
    public long position() {
      return record.getPosition();
    }
  }

  /**
   * A record whose value type this reader doesn't know.
   *
   * @param valueType the value type as encoded, unsigned
   * @param intent the intent as encoded, unsigned
   */
  record Unknown(long position, long key, long timestamp, int valueType, int intent)
      implements LogRecord {}
}
