/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

/**
 * Takes {@link ReadSnapshot}s of the durable store. Because the persist round is the only writer to
 * the underlying database, a snapshot taken at a quiescent boundary (right after a persist round
 * completes) captures exactly the cut that round produced — there is no need to snapshot at an
 * arbitrary past point.
 */
public interface SnapshotSource {

  /** Pins and returns a snapshot of the database as of now. The caller owns closing it. */
  ReadSnapshot takeSnapshot();
}
