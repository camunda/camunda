/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class GlobalListenerUpdateProcessor
    implements DistributedTypedRecordProcessor<GlobalListenerRecord> {

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public GlobalListenerUpdateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.distributionBehavior = distributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<GlobalListenerRecord> command) {
    final AuthorizationRequest authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.GLOBAL_LISTENER)
            .permissionType(PermissionType.UPDATE_TASK_LISTENER)
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      writers.rejection().appendRejection(command, rejection.type(), rejection.reason());
      writers.response().writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final long key = keyGenerator.nextKey();
    executeCommand(key, command.getValue());

    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.GLOBAL_LISTENERS.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GlobalListenerRecord> command) {
    executeCommand(command.getKey(), command.getValue());
    distributionBehavior.acknowledgeCommand(command);
  }

  private void executeCommand(final long key, final GlobalListenerRecord record) {
    // TODO: implement command logic
    writers.state().appendFollowUpEvent(key, GlobalListenerIntent.UPDATED, record);
  }
}
