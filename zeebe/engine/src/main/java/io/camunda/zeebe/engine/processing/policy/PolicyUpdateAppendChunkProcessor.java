/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicyAssembly;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accepts one chunk of a policy update. A chunk of a newer version supersedes an open assembly of
 * an older version. Chunks of the current or an older version are rejected, and so are chunks of a
 * version older than the open assembly.
 */
@NullMarked
public final class PolicyUpdateAppendChunkProcessor
    implements TypedRecordProcessor<PolicyUpdateRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(PolicyUpdateAppendChunkProcessor.class);

  private final PolicyUpdateState policyUpdateState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedCommandWriter commandWriter;

  public PolicyUpdateAppendChunkProcessor(
      final PolicyUpdateState policyUpdateState, final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    commandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<PolicyUpdateRecord> command) {
    final var record = command.getValue();
    LOG.debug(
        "Appending chunk {} of {} of policy update '{}' (version {}): tenants={}, roles={}, groups={}, mappingRules={}, memberships={}, authorizations={}, deletes={}, membershipDeletes={}, authorizationDeletes={}",
        record.getChunkIndex(),
        record.getChunkCount(),
        record.getEnvelopeId(),
        record.getPolicyVersion(),
        record.getTenants().size(),
        record.getRoles().size(),
        record.getGroups().size(),
        record.getMappingRules().size(),
        record.getMemberships().size(),
        record.getAuthorizations().size(),
        record.getDeletes().size(),
        record.getMembershipDeletes().size(),
        record.getAuthorizationDeletes().size());
    final var version = record.getPolicyVersion();
    final var currentVersion = policyUpdateState.getCurrentVersion();

    if (version == currentVersion) {
      reject(
          command,
          RejectionType.ALREADY_EXISTS,
          "Expected to append a chunk of policy version %d, but this version is already applied"
              .formatted(version));
      return;
    }
    if (version < currentVersion) {
      reject(
          command,
          RejectionType.INVALID_STATE,
          "Expected a policy version newer than %d, but got %d".formatted(currentVersion, version));
      return;
    }

    final var envelopeId = record.getEnvelopeId();
    final var assembly = policyUpdateState.getAssembly(envelopeId);
    final int receivedBefore = assembly.map(PersistedPolicyAssembly::getReceivedCount).orElse(0);
    if (assembly.isPresent()) {
      final var open = assembly.get();
      if (open.getPolicyVersion() != version || open.getChunkCount() != record.getChunkCount()) {
        reject(
            command,
            RejectionType.INVALID_ARGUMENT,
            "Expected chunks of policy version %d with %d chunks, but got version %d with %d chunks"
                .formatted(
                    open.getPolicyVersion(),
                    open.getChunkCount(),
                    version,
                    record.getChunkCount()));
        return;
      }
    } else {
      final var openAssembly = policyUpdateState.findOpenAssembly();
      if (openAssembly.isPresent() && !rejectOrAbortOpen(command, openAssembly.get())) {
        return;
      }
    }

    final var chunkIndex = record.getChunkIndex();
    if (chunkIndex < 0 || chunkIndex >= record.getChunkCount()) {
      reject(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected a chunk index between 0 and %d, but got %d"
              .formatted(record.getChunkCount() - 1, chunkIndex));
      return;
    }
    if (policyUpdateState.hasChunk(envelopeId, chunkIndex)) {
      reject(
          command,
          RejectionType.ALREADY_EXISTS,
          "Expected chunk %d of update '%s' to be new, but it was already received"
              .formatted(chunkIndex, envelopeId));
      return;
    }

    stateWriter.appendFollowUpEvent(command.getKey(), PolicyUpdateIntent.CHUNK_APPENDED, record);
    if (receivedBefore + 1 == record.getChunkCount()) {
      commandWriter.appendFollowUpCommand(
          command.getKey(), PolicyUpdateIntent.APPLY, progress(record, 0));
    }
  }

  static PolicyUpdateRecord progress(final PolicyUpdateRecord record, final int chunkIndex) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(record.getEnvelopeId())
        .setPolicyVersion(record.getPolicyVersion())
        .setChunkIndex(chunkIndex);
  }

  /**
   * Returns true if the new chunk may open its own assembly. An open assembly of an older version
   * is aborted and the caller continues. A newer or equally versioned open assembly rejects the
   * chunk and returns false.
   */
  private boolean rejectOrAbortOpen(
      final TypedRecord<PolicyUpdateRecord> command, final PersistedPolicyAssembly open) {
    final var version = command.getValue().getPolicyVersion();
    if (open.getPolicyVersion() >= version) {
      reject(
          command,
          RejectionType.INVALID_STATE,
          "Expected no update of policy version %d or older in progress, but update '%s' of version %d is in progress"
              .formatted(version, open.getEnvelopeId(), open.getPolicyVersion()));
      return false;
    }
    final var abort =
        new PolicyUpdateRecord()
            .setEnvelopeId(open.getEnvelopeId())
            .setPolicyVersion(open.getPolicyVersion());
    stateWriter.appendFollowUpEvent(command.getKey(), PolicyUpdateIntent.ABORTED, abort);
    return true;
  }

  private void reject(
      final TypedRecord<PolicyUpdateRecord> command,
      final RejectionType type,
      final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
  }
}
