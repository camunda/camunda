/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import java.util.function.Consumer;

public class NoneSnapshotReplication implements SnapshotReplication {

  @Override
  public void replicate(SnapshotChunk snapshot) {}

  @Override
  public void consume(Consumer<SnapshotChunk> consumer) {}

  @Override
  public void close() {}
}
