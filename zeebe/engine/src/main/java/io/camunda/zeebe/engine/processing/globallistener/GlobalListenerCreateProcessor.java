/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public final class GlobalListenerCreateProcessor
    implements DistributedTypedRecordProcessor<GlobalListenerRecord> {

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final GlobalListenersState globalListenersState;
  private final GlobalListenerValidator globalListenerValidator;

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
    globalListenerValidator = new GlobalListenerValidator();
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

    final var record = validRecord.get();
    // Generate a key for the new listener and one for the resulting new configuration
    final long listenerKey = keyGenerator.nextKey();
    final long configKey = keyGenerator.nextKey();
    record.setGlobalListenerKey(listenerKey);
    record.setConfigKey(configKey);

    emitChangeEvents(record);

    writers
        .response()
        .writeEventOnCommand(
            record.getGlobalListenerKey(), GlobalListenerIntent.CREATED, record, command);

    // Note: the configuration key is used as the command key for distribution, ensuring
    // configuration changes are applied in order
    distributionBehavior
        .withKey(configKey)
        .inQueue(DistributionQueue.GLOBAL_LISTENERS.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GlobalListenerRecord> command) {
    final var record = command.getValue();

    globalListenerValidator
        .listenerDoesNotExist(record, globalListenersState)
        .ifRightOrLeft(
            this::emitChangeEvents,
            rejection ->
                writers.rejection().appendRejection(command, rejection.type(), rejection.reason()));

    distributionBehavior.acknowledgeCommand(command);
  }

  private Either<Rejection, GlobalListenerRecord> validateCommand(
      final TypedRecord<GlobalListenerRecord> command) {
    final AuthorizationRequest authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.GLOBAL_LISTENER)
            .permissionType(PermissionType.CREATE_TASK_LISTENER)
            .build();
    return authCheckBehavior
        .isAuthorizedOrInternalCommand(authRequest)
        .map(unused -> command.getValue())
        .flatMap(globalListenerValidator::idProvided)
        .flatMap(
            record -> globalListenerValidator.listenerDoesNotExist(record, globalListenersState))
        .flatMap(globalListenerValidator::typeProvided)
        .flatMap(globalListenerValidator::eventTypesProvided)
        .flatMap(globalListenerValidator::validEventTypes);
  }

  private void emitChangeEvents(final GlobalListenerRecord record) {
    writers
        .state()
        .appendFollowUpEvent(record.getGlobalListenerKey(), GlobalListenerIntent.CREATED, record);
    writers
        .state()
        .appendFollowUpEvent(
            record.getConfigKey(),
            GlobalListenerBatchIntent.CONFIGURED,
            new GlobalListenerBatchRecord()
                .setGlobalListenerBatchKey(record.getGlobalListenerKey()));
  }
}
