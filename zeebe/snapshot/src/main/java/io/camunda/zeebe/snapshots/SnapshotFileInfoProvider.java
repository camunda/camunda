/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.nio.file.Path;

public interface SnapshotFileInfoProvider {

  /**
   * @param snapshotPath path of snapshot to get live file information for
   * @return the checksums and sizes of the snapshot's live files
   */
  SnapshotFilesInfo getSnapshotFilesInfo(final Path snapshotPath);
}
