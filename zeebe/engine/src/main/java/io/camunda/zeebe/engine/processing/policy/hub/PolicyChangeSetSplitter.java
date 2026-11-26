/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a Hub policy change set into the chunks of one policy update, which the engine buffers with
 * {@code APPEND_CHUNK} and applies in index order.
 *
 * <p>The splitter goes through the sections of the update in the order the engine applies them, and
 * fills one chunk after the other: when the next entry no longer fits into {@link #maxChunkSize}
 * bytes, it starts a new chunk. A chunk is applied section by section in that same order, so
 * applying the chunks one after the other keeps the order of the whole change set: a membership is
 * never added before its group exists, and a group is never deleted while a later entry still
 * refers to it.
 *
 * <p>Every chunk fits into {@link #maxChunkSize} bytes: the size of a chunk is counted for the
 * largest possible chunk index and count, and for the longest possible header of each list.
 */
@NullMarked
public final class PolicyChangeSetSplitter {

  /**
   * Leaves room for the other records of the batch that writes a chunk, and for the record
   * metadata, below the default maximum message size of 4 MB.
   */
  public static final int DEFAULT_MAX_CHUNK_SIZE = 1024 * 1024;

  /**
   * How many bytes the header of a list can grow by as the list gets longer: MessagePack encodes
   * the length of a list in 1 byte up to 15 entries, and in up to 5 bytes beyond.
   */
  private static final int MAX_LIST_HEADER_GROWTH = 4;

  private final int maxChunkSize;

  public PolicyChangeSetSplitter(final int maxChunkSize) {
    this.maxChunkSize = maxChunkSize;
  }

  /**
   * Splits the change set into the chunks of one policy update.
   *
   * @param changeSet the change set
   * @param seqNo the Hub sequence of the change set
   * @param updateId the ID of the policy update; a new one for every attempt to apply the change
   *     set, so that the engine never takes the chunks of an earlier attempt for this one
   * @return the chunks in index order, at least one; or why the change set cannot be applied
   */
  public Either<String, List<PolicyUpdateRecord>> split(
      final PolicyChangeSetV1 changeSet, final long seqNo, final String updateId) {
    return PolicyChangeSetMapper.map(changeSet, seqNo, updateId).flatMap(this::split);
  }

  private Either<String, List<PolicyUpdateRecord>> split(final PolicyUpdateRecord update) {
    try {
      final var chunks = new ChunkWriter(update);
      // in the order PolicyUpdateCommandMapper applies the sections
      chunks.add("tenants", update.getTenants(), PolicyUpdateRecord::setTenants);
      chunks.add("roles", update.getRoles(), PolicyUpdateRecord::setRoles);
      chunks.add("groups", update.getGroups(), PolicyUpdateRecord::setGroups);
      chunks.add("mappingRules", update.getMappingRules(), PolicyUpdateRecord::setMappingRules);
      chunks.add("memberships", update.getMemberships(), PolicyUpdateRecord::setMemberships);
      chunks.add(
          "authorizations", update.getAuthorizations(), PolicyUpdateRecord::setAuthorizations);
      chunks.add(
          "authorizationDeletes",
          update.getAuthorizationDeletes(),
          PolicyUpdateRecord::setAuthorizationDeletes);
      chunks.add(
          "membershipDeletes",
          update.getMembershipDeletes(),
          PolicyUpdateRecord::setMembershipDeletes);
      chunks.add("deletes", update.getDeletes(), PolicyUpdateRecord::setDeletes);
      return Either.right(chunks.finish());
    } catch (final CannotSplitException e) {
      return Either.left(e.reason);
    }
  }

  /** Fills the chunks of one update, one after the other. */
  private final class ChunkWriter {

    private final PolicyUpdateRecord update;
    private final List<PolicyUpdateRecord> chunks = new ArrayList<>();

    /** A chunk with only the identifiers of the update, to measure the size of one entry. */
    private final PolicyUpdateRecord probe;

    private final int probeSize;

    /** The size of a chunk without entries, with the largest chunk index and count. */
    private final int emptyChunkSize;

    private PolicyUpdateRecord chunk;
    private int chunkSize;

    private ChunkWriter(final PolicyUpdateRecord update) {
      this.update = update;
      probe = emptyChunk();
      probeSize = probe.getLength();
      emptyChunkSize =
          emptyChunk()
              .setChunkIndex(Integer.MAX_VALUE)
              .setChunkCount(Integer.MAX_VALUE)
              .getLength();
      if (emptyChunkSize > maxChunkSize) {
        throw new CannotSplitException(
            "Expected the identifiers of policy update '%s' to fit into a chunk of %d bytes, but they take %d bytes"
                .formatted(update.getUpdateId(), maxChunkSize, emptyChunkSize));
      }
      chunk = emptyChunk();
      chunkSize = emptyChunkSize;
    }

    /**
     * Adds the entries of one section to the current chunk, and starts a new chunk whenever the
     * next entry no longer fits.
     *
     * @param section the name of the section, for the error message
     * @param entries the entries of the section, in apply order
     * @param setter sets the section of a chunk to the given entries
     */
    private <T> void add(
        final String section,
        final List<T> entries,
        final BiConsumer<PolicyUpdateRecord, List<T>> setter) {
      // the entries of this section in the current chunk; earlier sections are already set on it
      var chunkEntries = new ArrayList<T>();
      // this section's list in the current chunk may get a longer header than in an empty chunk
      chunkSize += MAX_LIST_HEADER_GROWTH;
      for (final T entry : entries) {
        final int entrySize = sizeOf(entry, setter);
        // an entry that does not fit into an empty chunk fits into no chunk at all
        if (emptyChunkSize + MAX_LIST_HEADER_GROWTH + entrySize > maxChunkSize) {
          throw new CannotSplitException(
              "Expected every entry of policy change set of policy '%s' to fit into a chunk of %d bytes, but an entry of section %s has %d bytes"
                  .formatted(update.getPolicyId(), maxChunkSize, section, entrySize));
        }
        // the entry does not fit any more: close the chunk with this section's entries so far, and
        // continue the section in a new chunk
        if (chunkSize + entrySize > maxChunkSize) {
          setter.accept(chunk, chunkEntries);
          chunks.add(chunk);
          chunk = emptyChunk();
          chunkSize = emptyChunkSize + MAX_LIST_HEADER_GROWTH;
          chunkEntries = new ArrayList<>();
        }
        chunkEntries.add(entry);
        chunkSize += entrySize;
      }
      // the chunk stays open, so that the next section can fill up the rest of it
      setter.accept(chunk, chunkEntries);
    }

    /** The chunks in index order, at least one, each knowing its index and the chunk count. */
    private List<PolicyUpdateRecord> finish() {
      chunks.add(chunk);
      for (int index = 0; index < chunks.size(); index++) {
        chunks.get(index).setChunkIndex(index).setChunkCount(chunks.size());
      }
      return chunks;
    }

    /** How many bytes the entry adds to a chunk. */
    private <T> int sizeOf(final T entry, final BiConsumer<PolicyUpdateRecord, List<T>> setter) {
      setter.accept(probe, List.of(entry));
      final int size = probe.getLength() - probeSize;
      setter.accept(probe, List.of());
      return size;
    }

    private PolicyUpdateRecord emptyChunk() {
      return new PolicyUpdateRecord()
          .setPolicyId(update.getPolicyId())
          .setPolicyVersion(update.getPolicyVersion())
          .setBasePolicyVersion(update.getBasePolicyVersion())
          .setSeqNo(update.getSeqNo())
          .setUpdateId(update.getUpdateId());
    }
  }

  /** Why the change set cannot be split into chunks of the maximum size. */
  private static final class CannotSplitException extends RuntimeException {
    private final String reason;

    private CannotSplitException(final String reason) {
      super(reason, null, false, false);
      this.reason = reason;
    }
  }
}
