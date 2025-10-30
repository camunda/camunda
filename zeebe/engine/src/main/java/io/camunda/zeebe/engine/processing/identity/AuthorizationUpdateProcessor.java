/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.PermissionsBehavior.AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public class AuthorizationUpdateProcessor
    implements DistributedTypedRecordProcessor<AuthorizationRecord> {

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;
  private final PermissionsBehavior permissionsBehavior;
  private final AuthorizationEntityChecker authorizationEntityChecker;

  public AuthorizationUpdateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    authorizationCheckBehavior = authCheckBehavior;
    permissionsBehavior = new PermissionsBehavior(processingState, authCheckBehavior);
    authorizationEntityChecker = new AuthorizationEntityChecker(processingState);
  }

  @Override
  public void processNewCommand(final TypedRecord<AuthorizationRecord> command) {
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
        .flatMap(this::validateResourceIdOrPropertyName)
        .flatMap(record -> authorizationEntityChecker.ownerAndResourceExists(command))
        .ifRightOrLeft(
            authorizationRecord -> writeEventAndDistribute(command, authorizationRecord),
            (rejection) -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public void processDistributedCommand(final TypedRecord<AuthorizationRecord> command) {
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
                    authorizationCheckBehavior.clearAuthorizationsCache();
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
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        authorizationRecord.getAuthorizationKey(),
        AuthorizationIntent.UPDATED,
        authorizationRecord);
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
    responseWriter.writeEventOnCommand(
        authorizationRecord.getAuthorizationKey(),
        AuthorizationIntent.UPDATED,
        authorizationRecord,
        command);
    sideEffectWriter.appendSideEffect(
        () -> {
          authorizationCheckBehavior.clearAuthorizationsCache();
          return true;
        });
  }

  private Either<Rejection, AuthorizationRecord> validateResourceIdOrPropertyName(
      final AuthorizationRecord record) {
    final var matcher = record.getResourceMatcher();
    final var resourceId = record.getResourceId();
    final var resourcePropertyName = record.getResourcePropertyName();

    final boolean hasResourceId = !resourceId.isEmpty();
    final boolean hasPropertyName = !resourcePropertyName.isEmpty();

    switch (matcher) {
      case PROPERTY -> {
        // For PROPERTY matcher, propertyName must be provided and resourceId should be empty
        if (!hasPropertyName) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  "Expected to update authorization with matcher 'PROPERTY', but no resource property name was provided. Please provide a resource property name."));
        }
        if (hasResourceId) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  "Expected to update authorization with matcher 'PROPERTY', but both resource property name and resource ID were provided. Please provide only a resource property name."));
        }
      }
      case ID, ANY -> {
        // For ID and ANY matchers, resourceId should be provided and propertyName should be empty
        if (hasPropertyName) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  String.format(
                      "Expected to update authorization with matcher '%s', but a resource property name was provided. Resource property names are only valid for matcher 'PROPERTY'.",
                      matcher)));
        }
        if (!hasResourceId) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  String.format(
                      "Expected to update authorization with matcher '%s', but no resource ID was provided. Please provide a resource ID.",
                      matcher)));
        }
      }
    }

    return Either.right(record);
  }
}
