/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Constants of the event plane's segment format v1, as written by the cluster. */
final class SegmentV1 {

  static final int FOOTER_LENGTH = 16 + 8 * 6 + 1 + 1;
  static final int VERSION = 1;
  static final int LEVEL_PUBLISHER = 0;
  static final int KEY_IDENTITY = 1;
  static final int KEY_FRAME_INDEX = 2;
  static final int DIRECTORY_ENTRY_LENGTH = 2 + 4 + 4;
  static final int FRAME_INDEX_ENTRY_LENGTH = 16;

  /** The journal's frame version byte. */
  static final byte JOURNAL_FRAME_VERSION = 1;

  static final int ZSTD_LEVEL = 3;
  static final int ZSTD_WINDOW_LOG = 23;

  /** Smallest {@code RecordData}: SBE header, index, asqn and the blob length. */
  static final int MIN_RECORD_DATA_LENGTH = 8 + 8 + 8 + 4;

  private SegmentV1() {}

  static UUID history(final String timelineId) {
    return UUID.nameUUIDFromBytes(("log-archive:" + timelineId).getBytes(StandardCharsets.UTF_8));
  }
}
