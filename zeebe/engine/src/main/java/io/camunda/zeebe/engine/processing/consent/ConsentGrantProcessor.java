/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ConsentState;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Grants consent for every later request of the subject within one operation of one connection,
 * until revoked. Only the subject may grant: the caller's username must be the subject's, and no
 * permission overrides that, because a consent that someone else gives is not consent.
 *
 * <p>The grant is distributed to every partition through an ordered queue, so the jobs that wait on
 * it are released wherever they sit, and a revocation never overtakes the grant it revokes.
 *
 * <p>The subject check takes the place of a permission check, which is why the processor is
 * excluded from the rule that every processor checks a permission.
 */
@ExcludeAuthorizationCheck
public final class ConsentGrantProcessor implements DistributedTypedRecordProcessor<ConsentRecord> {

  private final ConsentState consentState;
  private final ConsentConnections connections;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public ConsentGrantProcessor(
      final ConsentState consentState,
      final ConsentConnections connections,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior) {
    this.consentState = consentState;
    this.connections = connections;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ConsentRecord> command) {
    final ConsentRecord grant = command.getValue();
    final String caller = callerOf(command);
    if (grant.getSubject().isEmpty()) {
      grant.setSubject(caller);
    }
    if (!grant.getSubject().equals(caller)) {
      reject(
          command,
          RejectionType.FORBIDDEN,
          "Only '%s' can grant consent for themselves; '%s' cannot grant it for them"
              .formatted(grant.getSubject(), caller));
      return;
    }
    final var operation = connections.findOperation(grant.getConnection(), grant.getOperation());
    if (operation.isLeft()) {
      reject(command, RejectionType.NOT_FOUND, operation.getLeft());
      return;
    }
    if (!operation.get().scope().allowsGrant()) {
      reject(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected the operation '%s' of the connection '%s' to accept a standing grant, but it accepts one-time approvals only"
              .formatted(grant.getOperation(), grant.getConnection()));
      return;
    }

    final var existing =
        consentState.getGrant(grant.getSubject(), grant.getConnection(), grant.getOperation());
    if (existing.isPresent()) {
      if (command.hasRequestMetadata()) {
        responseWriter.writeAcceptedResponseOnCommand(
            existing.get().getConsentKey(), ConsentIntent.GRANTED, existing.get(), command);
      }
      return;
    }

    final long consentKey = keyGenerator.nextKey();
    grant.setConsentKey(consentKey).setRequester(caller).setJobKey(-1L);
    stateWriter.appendFollowUpEvent(consentKey, ConsentIntent.GRANTED, grant);
    if (command.hasRequestMetadata()) {
      responseWriter.writeAcceptedResponseOnCommand(
          consentKey, ConsentIntent.GRANTED, grant, command);
    }
    releaseParkedJobs(grant);
    distributionBehavior
        .withKey(consentKey)
        .inQueue(DistributionQueue.CONSENT.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ConsentRecord> command) {
    final ConsentRecord grant = command.getValue();
    if (consentState.getGrantByKey(grant.getConsentKey()).isEmpty()) {
      stateWriter.appendFollowUpEvent(grant.getConsentKey(), ConsentIntent.GRANTED, grant);
      releaseParkedJobs(grant);
    }
    distributionBehavior.acknowledgeCommand(command);
  }

  private void releaseParkedJobs(final ConsentRecord grant) {
    commandWriter.appendFollowUpCommand(
        keyGenerator.nextKey(),
        ConsentIntent.RELEASE,
        new ConsentRecord()
            .setSubject(grant.getSubject())
            .setConnection(grant.getConnection())
            .setOperation(grant.getOperation())
            .setConsentKey(grant.getConsentKey())
            .setJobKey(-1L));
  }

  private void reject(
      final TypedRecord<ConsentRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectedResponseOnCommand(command, type, reason);
    }
  }

  static String callerOf(final TypedRecord<?> command) {
    final Object username = command.getAuthorizations().get(Authorization.AUTHORIZED_USERNAME);
    return username instanceof final String name ? name : "";
  }
}
