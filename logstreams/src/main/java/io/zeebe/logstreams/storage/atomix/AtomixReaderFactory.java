/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.RaftLogReader.Mode;

@FunctionalInterface
public interface AtomixReaderFactory {
  RaftLogReader create(Mode mode);

  default RaftLogReader create() {
    return create(Mode.COMMITS);
  }
}
