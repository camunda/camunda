/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class ClockResetProcessor implements DistributedTypedRecordProcessor<ClockRecord> {

  private final SideEffectWriter sideEffectWriter;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ControllableStreamClock clock;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public ClockResetProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ControllableStreamClock clock,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    sideEffectWriter = writers.sideEffect();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.clock = clock;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClockRecord> command) {
    // Validate authorization
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.SYSTEM)
            .permissionType(PermissionType.UPDATE)
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    // Process command
    final var clockRecord = command.getValue();
    final long eventKey = keyGenerator.nextKey();
    applyClockModification(eventKey, clockRecord);
    if (command.hasRequestMetadata()) {
      responseWriter.writeEventOnCommand(eventKey, ClockIntent.RESETTED, clockRecord, command);
    }

    // Distribute to other partitions
    commandDistributionBehavior.withKey(eventKey).unordered().distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClockRecord> command) {
    applyClockModification(command.getKey(), command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void applyClockModification(final long key, final ClockRecord clockRecord) {
    final SideEffectProducer sideEffect =
        () -> {
          clock.reset();
          return true;
        };
    sideEffectWriter.appendSideEffect(sideEffect);
    stateWriter.appendFollowUpEvent(key, ClockIntent.RESETTED, clockRecord);
  }
}
