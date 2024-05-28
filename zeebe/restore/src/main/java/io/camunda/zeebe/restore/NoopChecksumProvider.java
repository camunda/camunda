/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.snapshots.ChecksumProvider;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class NoopChecksumProvider implements ChecksumProvider {
  @Override
  public Map<String, Long> getSnapshotChecksums(final Path snapshotPath) {
    return Collections.emptyMap();
  }
}
