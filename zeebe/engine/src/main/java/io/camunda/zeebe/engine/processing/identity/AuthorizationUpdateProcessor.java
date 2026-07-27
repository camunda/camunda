/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.PermissionsBehavior.AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.AuthorizationScopeStateAdapter;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

@NullMarked
public class AuthorizationUpdateProcessor
    implements DistributedTypedRecordProcessor<AuthorizationRecord> {

  private static final Logger LOG = Loggers.ENGINE_IDENTITY_LOGGER;

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final PermissionsBehavior permissionsBehavior;
  private final AuthorizationEntityValidator authorizationEntityChecker;
  private final AuthorizationScopeStateAdapter authorizationScopeStateAdapter;

  public AuthorizationUpdateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior distributionBehavior,
      final CslAuthorizationCheck cslCheck,
      final EngineSecurityConfig securityConfig,
      final AuthorizationScopeStateAdapter authorizationScopeStateAdapter) {
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    permissionsBehavior = new PermissionsBehavior(processingState, cslCheck);
    authorizationEntityChecker = new AuthorizationEntityValidator(processingState, securityConfig);
    this.authorizationScopeStateAdapter = authorizationScopeStateAdapter;
  }

  @Override
  public void processNewCommand(final TypedRecord<AuthorizationRecord> command) {
    LOG.debug(
        "Processing UPDATE authorization command for key {}",
        command.getValue().getAuthorizationKey());
    permissionsBehavior
        .isAuthorized(command)
        .flatMap(
            authorizationRecord ->
                permissionsBehavior.authorizationExists(
                    authorizationRecord, AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE))
        .flatMap(
            record ->
                permissionsBehavior.hasValidPermissionTypes(
                    command.getValue(),
                    command.getValue().getPermissionTypes(),
                    record.getResourceType(),
                    "Expected to update authorization with permission types '%s' and resource type '%s', but these permissions are not supported. Supported permission types are: '%s'"))
        .flatMap(record -> authorizationEntityChecker.validateResourceMatcher(record, "update"))
        .flatMap(record -> authorizationEntityChecker.ownerAndResourceExists(command))
        .ifRightOrLeft(
            authorizationRecord -> writeEventAndDistribute(command, authorizationRecord),
            (rejection) -> {
              LOG.debug("Rejecting UPDATE authorization command: {}", rejection.reason());
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public void processDistributedCommand(final TypedRecord<AuthorizationRecord> command) {
    LOG.debug(
        "Processing distributed UPDATE authorization command for key {}",
        command.getValue().getAuthorizationKey());
    permissionsBehavior
        .authorizationExists(command.getValue(), AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE)
        .flatMap(s -> authorizationEntityChecker.ownerAndResourceExists(command))
        .ifRightOrLeft(
            ignored -> {
              stateWriter.appendFollowUpEvent(
                  command.getValue().getAuthorizationKey(),
                  AuthorizationIntent.UPDATED,
                  command.getValue());
              sideEffectWriter.appendSideEffect(
                  () -> {
                    authorizationScopeStateAdapter.invalidateAll();
                    return true;
                  });
            },
            rejection ->
                rejectionWriter.appendRejection(command, rejection.type(), rejection.reason()));

    distributionBehavior.acknowledgeCommand(command);
  }

  private void writeEventAndDistribute(
      final TypedRecord<AuthorizationRecord> command,
      final AuthorizationRecord authorizationRecord) {
    LOG.debug("Updating authorization with key {}", authorizationRecord.getAuthorizationKey());
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        authorizationRecord.getAuthorizationKey(),
        AuthorizationIntent.UPDATED,
        authorizationRecord);
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
    responseWriter.writeAcceptedResponseOnCommand(
        authorizationRecord.getAuthorizationKey(),
        AuthorizationIntent.UPDATED,
        authorizationRecord,
        command);
    sideEffectWriter.appendSideEffect(
        () -> {
          authorizationScopeStateAdapter.invalidateAll();
          return true;
        });
  }
}
