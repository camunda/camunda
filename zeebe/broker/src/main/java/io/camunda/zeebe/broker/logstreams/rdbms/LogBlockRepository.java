/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import java.util.List;
import java.util.Optional;

/** Stores the log blocks of one partition, and the term of the leader allowed to append them. */
public interface LogBlockRepository {

  /**
   * Claims the partition for a leader of the given term. Succeeds if no higher term has claimed it
   * yet; claiming the term that already holds the partition again succeeds too.
   *
   * @return true if the partition is now held by {@code term}, false if a higher term holds it
   */
  boolean claimTerm(long term);

  /**
   * Appends the blocks atomically, provided the partition is still held by {@code term}: either all
   * of them are written or none.
   *
   * @throws TermMismatchException if the partition is not held by {@code term}
   * @throws LogBlockRepositoryException if the blocks could not be written, e.g. because a block at
   *     the same low position already exists
   */
  void append(long term, List<LogBlock> blocks);

  /**
   * @return the block with the highest low position that is at or before {@code position}, or empty
   *     if every block starts after it
   */
  Optional<LogBlock> findBlockAtOrBefore(long position);

  /**
   * @return up to {@code maxBlocks} blocks whose low position is after {@code lowPosition}, in
   *     position order
   */
  List<LogBlock> readBlocksAfter(long lowPosition, int maxBlocks);
}
