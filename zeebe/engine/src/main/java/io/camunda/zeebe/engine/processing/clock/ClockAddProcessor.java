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
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ClockState;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification.None;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification.Offset;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification.Pin;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

/**
 * Adds a duration to the clock. A pinned clock stays pinned and moves to the new time. Otherwise,
 * the clock keeps running from the new time.
 *
 * <p>The command holds the duration to add, which is relative to the clock of this partition. The
 * other partitions receive the result instead: a {@link ClockIntent#PIN} command with the new
 * pinned time, or a {@link ClockIntent#OFFSET} command with the new total offset. This way, a
 * partition that receives the command twice does not add the duration twice.
 *
 * <p>The new total offset is only right if this partition has seen every earlier clock change. So
 * the gateway sends all clock changes to the deployment partition, and they reach the other
 * partitions in the same order through the {@link DistributionQueue#CLOCK} queue.
 */
@NullMarked
public final class ClockAddProcessor implements TypedRecordProcessor<ClockRecord> {

  private final SideEffectWriter sideEffectWriter;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final ClockState clockState;
  private final ControllableStreamClock clock;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final CslAuthorizationCheck cslCheck;
  private final ClockRecord modifiedRecord = new ClockRecord();
  private final ClockRecord responseRecord = new ClockRecord();

  public ClockAddProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ClockState clockState,
      final ControllableStreamClock clock,
      final CommandDistributionBehavior commandDistributionBehavior,
      final CslAuthorizationCheck cslCheck) {
    sideEffectWriter = writers.sideEffect();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.clockState = clockState;
    this.clock = clock;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.cslCheck = cslCheck;
  }

  @Override
  public void processRecord(final TypedRecord<ClockRecord> command) {
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

    final long offset = command.getValue().getTime();
    final var modification = clockState.getModification();
    // The clock itself is only updated in a side effect, which runs after the previous change is
    // committed. Until then it can be behind the state, so the time is based on the state.
    final long currentTime =
        switch (modification) {
          case Pin(final var pinnedAt) -> pinnedAt.toEpochMilli();
          case Offset(final var by) -> clock.systemMillis() + by.toMillis();
          case None() -> clock.systemMillis();
        };

    if (offset > 0 && currentTime > Long.MAX_VALUE - offset) {
      reject(
          command,
          ("Expected the new clock time to be at most %d, but adding %d ms to the current time %d"
                  + " goes beyond that")
              .formatted(Long.MAX_VALUE, offset, currentTime));
      return;
    }

    final long newTime = currentTime + offset;
    if (newTime < 0) {
      reject(
          command,
          ("Expected the new clock time to be not negative, but adding %d ms to the current time"
                  + " %d makes it %d")
              .formatted(offset, currentTime, newTime));
      return;
    }

    final long eventKey = keyGenerator.nextKey();
    final ClockIntent eventIntent;
    final ClockIntent distributedIntent;
    if (modification instanceof Pin) {
      modifiedRecord.pinAt(newTime);
      eventIntent = ClockIntent.PINNED;
      distributedIntent = ClockIntent.PIN;
      final var pinnedAt = Instant.ofEpochMilli(newTime);
      sideEffectWriter.appendSideEffect(() -> clock.pinAt(pinnedAt));
    } else {
      final long currentOffset = modification instanceof Offset(final var by) ? by.toMillis() : 0;
      modifiedRecord.offsetBy(currentOffset + offset);
      eventIntent = ClockIntent.OFFSETTED;
      distributedIntent = ClockIntent.OFFSET;
      final var newOffset = Duration.ofMillis(modifiedRecord.getTime());
      sideEffectWriter.appendSideEffect(() -> clock.offsetBy(newOffset));
    }

    stateWriter.appendFollowUpEvent(eventKey, eventIntent, modifiedRecord);
    if (command.hasRequestMetadata()) {
      // an OFFSETTED event holds the total offset, but the caller wants the new clock time
      responseRecord.setTime(newTime);
      responseWriter.writeAcceptedResponseOnCommand(eventKey, eventIntent, responseRecord, command);
    }

    commandDistributionBehavior
        .withKey(eventKey)
        .inQueue(DistributionQueue.CLOCK)
        .distribute(ValueType.CLOCK, distributedIntent, modifiedRecord, command.getAuthInfo());
  }

  private void reject(final TypedRecord<ClockRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
    responseWriter.writeRejectedResponseOnCommand(command, RejectionType.INVALID_ARGUMENT, reason);
  }
}
