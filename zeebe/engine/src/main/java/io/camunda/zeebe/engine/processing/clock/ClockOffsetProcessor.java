/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;

/**
 * Sets the total offset between the clock and the system time, replacing any previous pin or
 * offset. The clock keeps running from the new time.
 *
 * <p>The {@link ClockAddProcessor} sends this command to the other partitions. The command holds
 * the total offset, not a change to it, so a partition that receives it twice ends up with the same
 * clock.
 */
@NullMarked
public final class ClockOffsetProcessor implements DistributedTypedRecordProcessor<ClockRecord> {

  private final SideEffectWriter sideEffectWriter;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ControllableStreamClock clock;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final CslAuthorizationCheck cslCheck;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public ClockOffsetProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ControllableStreamClock clock,
      final CommandDistributionBehavior commandDistributionBehavior,
      final CslAuthorizationCheck cslCheck) {
    sideEffectWriter = writers.sideEffect();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.clock = clock;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.cslCheck = cslCheck;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClockRecord> command) {
    final var isAuthorized =
        cslCheck.check(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.SYSTEM))
                        .permissionType(AuthzModelMapper.fromProtocol(PermissionType.UPDATE))
                        .resourceId(AuthorizationScope.WILDCARD_CHAR)),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.UPDATE, AuthorizationResourceType.SYSTEM));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var clockRecord = command.getValue();
    final long eventKey = keyGenerator.nextKey();
    applyClockModification(eventKey, clockRecord);
    if (command.hasRequestMetadata()) {
      responseWriter.writeAcceptedResponseOnCommand(
          eventKey, ClockIntent.OFFSETTED, clockRecord, command);
    }

    commandDistributionBehavior
        .withKey(eventKey)
        .inQueue(DistributionQueue.CLOCK)
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClockRecord> command) {
    applyClockModification(command.getKey(), command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void applyClockModification(final long key, final ClockRecord clockRecord) {
    final var offset = Duration.ofMillis(clockRecord.getTime());
    final SideEffectProducer sideEffect = () -> clock.offsetBy(offset);
    sideEffectWriter.appendSideEffect(sideEffect);
    stateWriter.appendFollowUpEvent(key, ClockIntent.OFFSETTED, clockRecord);
  }
}
