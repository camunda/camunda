/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

/**
 * Holds transactions back from the storage until the log has committed the records they depend on.
 * A storage that outlives the leader, like a database that all replicas share, must only ever hold
 * the effects of committed records.
 *
 * <p>When a transaction commits in the cache, the cache stores {@link #lastWrittenPosition()} with
 * it. The writer only writes it once {@link #committedPosition()} has reached that position. It
 * keeps the commit order: a transaction waiting for the log also holds back the ones after it.
 *
 * <p>The committed position must only move forward while this replica is the leader that opened the
 * database. A follower learns committed positions from a new leader, whose records can sit at the
 * positions of records this leader wrote but never committed.
 */
public interface CommitGate {

  /**
   * Returns the position of the last record written to the log. Every record a transaction
   * committing now depends on is at or below it.
   */
  long lastWrittenPosition();

  /** Returns the position up to which the log is committed. */
  long committedPosition();

  /**
   * Calls {@code listener} after each time {@link #committedPosition()} moves forward. It may be
   * called on any thread, and returns quickly.
   */
  void addCommitListener(Runnable listener);

  void removeCommitListener(Runnable listener);
}
