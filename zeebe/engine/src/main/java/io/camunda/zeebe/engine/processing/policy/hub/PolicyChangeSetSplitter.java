/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSetEntries.Entry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a Hub policy change set into the chunks of one policy update, which the engine buffers with
 * {@code APPEND_CHUNK} and applies in index order.
 *
 * <p>The entries of the change set are listed in the order the engine applies them, and each chunk
 * takes the next unbroken run of them that fits into {@link #maxChunkSize} bytes. A chunk is
 * applied section by section in that same order, so applying the chunks one after the other keeps
 * the order of the whole change set: a membership is never added before its group exists, and a
 * group is never deleted while a later entry still refers to it.
 *
 * <p>The split depends only on the change set, its sequence and the chunk size, so a change set
 * that is received again is cut into the same chunks under the same update ID. The engine relies on
 * this when it ignores a chunk it has already buffered.
 */
@NullMarked
public final class PolicyChangeSetSplitter {

  /**
   * Leaves room for the other records of the scheduler's batch and the record metadata, below the
   * default maximum message size of 4 MB.
   */
  public static final int DEFAULT_MAX_CHUNK_SIZE = 1024 * 1024;

  private final int maxChunkSize;

  public PolicyChangeSetSplitter(final int maxChunkSize) {
    this.maxChunkSize = maxChunkSize;
  }

  /**
   * Splits the change set into the chunks of one policy update.
   *
   * @param changeSet the change set
   * @param seqNo the Hub sequence of the change set
   * @return the chunks in index order, at least one; or why the change set cannot be applied
   */
  public Either<String, List<PolicyUpdateRecord>> split(
      final PolicyChangeSetV1 changeSet, final long seqNo) {
    return PolicyChangeSetMapper.map(changeSet, seqNo).flatMap(this::split);
  }

  private Either<String, List<PolicyUpdateRecord>> split(final PolicyChangeSetEntries changeSet) {
    final int headerSize = changeSet.header().getLength();
    final var chunks = new ArrayList<List<Entry>>();
    var chunk = new ArrayList<Entry>();
    int chunkSize = headerSize;
    for (final var entry : changeSet.entries()) {
      final int entrySize = sizeOf(changeSet, entry, headerSize);
      if (headerSize + entrySize > maxChunkSize) {
        return Either.left(
            "Expected every entry of policy change set of policy '%s' to fit into a chunk of %d bytes, but an entry of section %s has %d bytes"
                .formatted(
                    changeSet.header().getPolicyId(), maxChunkSize, entry.section(), entrySize));
      }
      if (chunkSize + entrySize > maxChunkSize) {
        chunks.add(chunk);
        chunk = new ArrayList<>();
        chunkSize = headerSize;
      }
      chunk.add(entry);
      chunkSize += entrySize;
    }
    chunks.add(chunk);

    final int chunkCount = chunks.size();
    final var records = new ArrayList<PolicyUpdateRecord>(chunkCount);
    for (int index = 0; index < chunkCount; index++) {
      records.add(
          changeSet.chunk(chunks.get(index)).setChunkIndex(index).setChunkCount(chunkCount));
    }
    return Either.right(records);
  }

  /** How many bytes the entry adds to a chunk. */
  private static int sizeOf(
      final PolicyChangeSetEntries changeSet, final Entry entry, final int headerSize) {
    return changeSet.chunk(List.of(entry)).getLength() - headerSize;
  }
}
