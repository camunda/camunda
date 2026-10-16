/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

/** Constants of log object format v1. All integers are big-endian. */
final class LogObjectFormat {
  static final int HEADER_MAGIC = 0x5A4C4131; // "ZLA1"
  static final int TRAILER_MAGIC = 0x5A4C4145; // "ZLAE"

  /** magic, headerLength, partitionId, term, firstIndex, lastIndex, firstPos, lastPos, count */
  static final int HEADER_FIXED_LENGTH = 4 + 4 + 4 + 8 + 8 + 8 + 8 + 8 + 4;

  static final int TIMELINE_LENGTH_FIELD = 2;
  static final int FRAME_FIXED_LENGTH = 8 * 6 + 4;
  static final int FOOTER_ENTRY_LENGTH = 24;
  static final int TRAILER_LENGTH = 12;
  static final int SPARSE_INTERVAL = 64;

  private LogObjectFormat() {}
}
