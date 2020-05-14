/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.atomix.raft.impl.zeebe.snapshot;

import java.nio.file.Path;
import java.util.Comparator;

public interface Snapshot extends Comparable<Snapshot> {

  /**
   * Returns an implementation specific compaction bound, e.g. a log stream position, a timestamp,
   * etc., used during compaction
   *
   * @return the compaction upper bound
   */
  long getCompactionBound();

  Path getPath();

  @Override
  default int compareTo(final Snapshot other) {
    return Comparator.comparing(Snapshot::getCompactionBound).compare(this, other);
  }
}
