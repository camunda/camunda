/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ConsentState;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Revokes a grant, by its key. The subject may revoke their own grant. An admin may revoke anyone's
 * with a reason, because removing authority never acts for anyone: whoever may update the
 * connection's cluster variable counts as its admin.
 *
 * <p>Revocation is prospective. It stops every hand-out from then on and does not reach a job that
 * a worker already holds. The grant record stays in the log: a revocation does not unmake the fact
 * that consent was given once.
 */
public final class ConsentRevokeProcessor
    implements DistributedTypedRecordProcessor<ConsentRecord> {

  private final ConsentState consentState;
  private final PermissionsBehavior permissionsBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public ConsentRevokeProcessor(
      final ConsentState consentState,
      final PermissionsBehavior permissionsBehavior,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior) {
    this.consentState = consentState;
    this.permissionsBehavior = permissionsBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ConsentRecord> command) {
    final long consentKey =
        command.getKey() > 0 ? command.getKey() : command.getValue().getConsentKey();
    final var grant = consentState.getGrantByKey(consentKey);
    if (grant.isEmpty()) {
      reject(
          command,
          RejectionType.NOT_FOUND,
          "Expected to revoke the grant with key '%d', but no such grant is live"
              .formatted(consentKey));
      return;
    }
    final String caller = ConsentGrantProcessor.callerOf(command);
    final String reason = command.getValue().getReason();
    if (!grant.get().getSubject().equals(caller)) {
      if (reason.isBlank()) {
        reject(
            command,
            RejectionType.FORBIDDEN,
            "Only '%s' can revoke their grant without a reason; an admin must give one"
                .formatted(grant.get().getSubject()));
        return;
      }
      final var adminCheck =
          permissionsBehavior.isAuthorized(
              command,
              AuthorizationResourceType.CLUSTER_VARIABLE,
              PermissionType.UPDATE,
              ConsentConnections.VARIABLE_PREFIX + grant.get().getConnection());
      if (adminCheck.isLeft()) {
        reject(command, adminCheck.getLeft().type(), adminCheck.getLeft().reason());
        return;
      }
    }

    final ConsentRecord revocation = grant.get().setReason(reason).setRequester(caller);
    stateWriter.appendFollowUpEvent(consentKey, ConsentIntent.REVOKED, revocation);
    if (command.hasRequestMetadata()) {
      responseWriter.writeAcceptedResponseOnCommand(
          consentKey, ConsentIntent.REVOKED, revocation, command);
    }
    command.getValue().setConsentKey(consentKey);
    distributionBehavior
        .withKey(consentKey)
        .inQueue(DistributionQueue.CONSENT.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ConsentRecord> command) {
    consentState
        .getGrantByKey(command.getValue().getConsentKey())
        .ifPresent(
            grant ->
                stateWriter.appendFollowUpEvent(
                    grant.getConsentKey(),
                    ConsentIntent.REVOKED,
                    grant.setReason(command.getValue().getReason())));
    distributionBehavior.acknowledgeCommand(command);
  }

  private void reject(
      final TypedRecord<ConsentRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectedResponseOnCommand(command, type, reason);
    }
  }
}
