/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.nio.channels.FileChannel;
import java.util.List;

public sealed interface IndexScanResult {

  record Success(FileChannel channel, List<IndexEntry> entries, ResourceLease lease)
      implements IndexScanResult {}

  enum Truncated implements IndexScanResult {
    INSTANCE
  }

  enum EndOfLog implements IndexScanResult {
    INSTANCE
  }

  enum FutureOffset implements IndexScanResult {
    INSTANCE
  }
}
