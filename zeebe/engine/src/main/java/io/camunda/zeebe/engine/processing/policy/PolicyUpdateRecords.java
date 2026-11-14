/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class PolicyUpdateRecords {

  private PolicyUpdateRecords() {}

  /**
   * The identifiers of the update with the given chunk index, but no policy data. Only {@code
   * APPEND_CHUNK} and {@code CHUNK_APPENDED} carry policy data; the other records refer to a
   * buffered chunk, so they don't write it into the log again.
   */
  static PolicyUpdateRecord withoutPolicyData(
      final PolicyUpdateRecordValue update, final int chunkIndex) {
    return new PolicyUpdateRecord()
        .setPolicyId(update.getPolicyId())
        .setPolicyVersion(update.getPolicyVersion())
        .setBasePolicyVersion(update.getBasePolicyVersion())
        .setSeqNo(update.getSeqNo())
        .setUpdateId(update.getUpdateId())
        .setChunkIndex(chunkIndex)
        .setChunkCount(update.getChunkCount());
  }

  /**
   * The identifiers and chunk count in which the chunk differs from its update, each as {@code
   * "<name> <chunk's value> instead of <update's value>"}. Empty if the chunk matches its update.
   */
  static List<String> differingIdentifiers(
      final PolicyUpdateRecordValue update, final PolicyUpdateRecordValue chunk) {
    return Stream.of(
            difference("policyId", chunk.getPolicyId(), update.getPolicyId()),
            difference("policyVersion", chunk.getPolicyVersion(), update.getPolicyVersion()),
            difference(
                "basePolicyVersion", chunk.getBasePolicyVersion(), update.getBasePolicyVersion()),
            difference("seqNo", chunk.getSeqNo(), update.getSeqNo()),
            difference("chunkCount", chunk.getChunkCount(), update.getChunkCount()))
        .flatMap(Optional::stream)
        .toList();
  }

  /** The difference as {@code "<name> <chunk's value> instead of <update's value>"}, if any. */
  private static Optional<String> difference(
      final String name, final Object chunkValue, final Object updateValue) {
    return Objects.equals(chunkValue, updateValue)
        ? Optional.empty()
        : Optional.of("%s %s instead of %s".formatted(name, chunkValue, updateValue));
  }
}
