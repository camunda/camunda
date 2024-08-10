/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.nio.file.Path;
import java.util.Map;

public class TestChecksumProvider implements CRC32CChecksumProvider {
  private final Map<String, Long> snapshotChecksums;

  public TestChecksumProvider(final Map<String, Long> snapshotChecksums) {
    this.snapshotChecksums = snapshotChecksums;
  }

  @Override
  public Map<String, Long> getSnapshotChecksums(final Path snapshotPath) {
    return snapshotChecksums;
  }
}
