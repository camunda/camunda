/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.WrittenRecord;
import io.camunda.zeebe.util.buffer.BufferWriter;

/** Estimates how much memory exporting a record takes, from the record's own size. */
public final class RecordSizes {

  private RecordSizes() {}

  /**
   * The record's length in the log, if it was read from one, else its serialized length, else its
   * value's serialized length, as for a record copied from the log, else the length of its string
   * form. The broker hands exporters records read from the log, so only records that tests build
   * reach the last fallback; their string form lists every field.
   */
  public static long of(final Record<?> record) {
    if (record instanceof final WrittenRecord writtenRecord) {
      return writtenRecord.getRawLength();
    }
    if (record instanceof final BufferWriter writer) {
      return writer.getLength();
    }
    // a copied record's string form is cut short, but its value is still serialized
    if (record.getValue() instanceof final BufferWriter value) {
      return value.getLength();
    }
    return record.toString().length();
  }
}
