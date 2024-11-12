/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface RestorableSnapshotStore {

  /**
   * Restores the snapshot by moving the snapshotFiles to the snapshotDirectory.
   *
   * <p>WARN. Implementation of this method can be not thread safe.
   *
   * @param snapshotId
   * @param snapshotFiles
   * @throws IOException
   */
  void restore(String snapshotId, Map<String, Path> snapshotFiles) throws IOException;
}
