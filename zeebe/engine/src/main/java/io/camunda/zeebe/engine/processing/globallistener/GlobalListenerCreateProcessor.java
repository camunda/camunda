/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

@ExcludeAuthorizationCheck
public final class GlobalListenerCreateProcessor
    implements DistributedTypedRecordProcessor<GlobalListenerRecord> {

  private static final String LISTENER_EXISTS_ERROR_MESSAGE =
      "Expected to create a global %s listener with id '%s', but a listener with the same id was found";

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final GlobalListenersState globalListenersState;

  public GlobalListenerCreateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior,
      final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.distributionBehavior = distributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void processNewCommand(final TypedRecord<GlobalListenerRecord> command) {
    final var validRecord = validateCommand(command);
    if (validRecord.isLeft()) {
      final var rejection = validRecord.getLeft();
      writers.rejection().appendRejection(command, rejection.type(), rejection.reason());
      writers.response().writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final long key = keyGenerator.nextKey();
    writers.state().appendFollowUpEvent(key, GlobalListenerIntent.CREATED, validRecord.get());

    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.GLOBAL_LISTENERS.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GlobalListenerRecord> command) {
    final var record = command.getValue();

    final var listener =
        globalListenersState.getGlobalListener(record.getListenerType(), record.getId());
    if (listener != null) {
      final var message =
          LISTENER_EXISTS_ERROR_MESSAGE.formatted(record.getListenerType(), record.getId());
      writers.rejection().appendRejection(command, RejectionType.ALREADY_EXISTS, message);
    } else {
      writers.state().appendFollowUpEvent(command.getKey(), GlobalListenerIntent.CREATED, record);
    }
    distributionBehavior.acknowledgeCommand(command);
  }

  private Either<Rejection, GlobalListenerRecord> validateCommand(
      final TypedRecord<GlobalListenerRecord> command) {
    return Either.right(command.getValue());
  }
}
