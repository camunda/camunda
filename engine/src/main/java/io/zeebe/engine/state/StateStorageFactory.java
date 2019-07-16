/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import io.zeebe.logstreams.state.StateStorage;
import java.io.File;

/**
 * This class may eventually be superseded by a more accurate StateStorage class local to the broker
 * core module if it ever needs more functionality than strictly creating stream processor specific
 * storage classes (e.g. listing all of them for regular maintenance jobs). If you find yourself
 * adding such functionality consider refactoring the whole thing.
 */
public class StateStorageFactory {
  public static final String DEFAULT_RUNTIME_PATH = "runtime";
  public static final String DEFAULT_SNAPSHOTS_PATH = "snapshots";

  private final File rootDirectory;

  public StateStorageFactory(final File rootDirectory) {
    this.rootDirectory = rootDirectory;
  }

  public StateStorage create() {
    return create(null);
  }

  public StateStorage createTemporary(String tmpSuffix) {
    return create(tmpSuffix);
  }

  private StateStorage create(final String tmpSuffix) {
    final File runtimeDirectory = new File(rootDirectory, DEFAULT_RUNTIME_PATH);
    final File snapshotsDirectory = new File(rootDirectory, DEFAULT_SNAPSHOTS_PATH);

    if (!runtimeDirectory.exists()) {
      runtimeDirectory.mkdir();
    }

    if (!snapshotsDirectory.exists()) {
      snapshotsDirectory.mkdir();
    }

    if (tmpSuffix != null) {
      return new StateStorage(runtimeDirectory, snapshotsDirectory, tmpSuffix);
    } else {
      return new StateStorage(runtimeDirectory, snapshotsDirectory);
    }
  }
}
