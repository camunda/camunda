/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Left;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterVariableUpdateProcessor
    implements DistributedTypedRecordProcessor<ClusterVariableRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClusterVariableUpdateProcessor.class);
  private static final String NOT_FOUND_FOR_TENANT_MESSAGE =
      "Expected to perform operation '%s' on resource '%s', but no resource was found for tenant '%s'";

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final CslAuthorizationCheck cslCheck;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ClusterVariableRecordValidator clusterVariableRecordValidator;

  public ClusterVariableUpdateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ClusterVariableRecordValidator clusterVariableRecordValidator) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.cslCheck = cslCheck;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.clusterVariableRecordValidator = clusterVariableRecordValidator;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClusterVariableRecord> command) {
    final ClusterVariableRecord commandRecord = command.getValue();
    clusterVariableRecordValidator
        .ensureValidScope(commandRecord)
        .flatMap(clusterVariableRecordValidator::loadExisting)
        .map(stored -> applyUpdate(stored, commandRecord))
        .flatMap(record -> isAuthorized(record, command))
        .ifRightOrLeft(
            record -> {
              final long key = keyGenerator.nextKey();
              writers.state().appendFollowUpEvent(key, ClusterVariableIntent.UPDATED, record);
              writers
                  .response()
                  .writeAcceptedResponseOnCommand(
                      key, ClusterVariableIntent.UPDATED, record, command);
              commandDistributionBehavior
                  .withKey(key)
                  .inQueue(record.getName())
                  .distribute(command);
            },
            rejection -> {
              writers.rejection().appendRejection(command, rejection.type(), rejection.reason());
              writers
                  .response()
                  .writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClusterVariableRecord> command) {
    final var commandRecord = command.getValue();
    clusterVariableRecordValidator
        .loadExisting(commandRecord)
        .map(stored -> applyUpdate(stored, commandRecord))
        .ifRightOrLeft(
            record ->
                writers
                    .state()
                    .appendFollowUpEvent(command.getKey(), ClusterVariableIntent.UPDATED, record),
            rejection ->
                writers.rejection().appendRejection(command, rejection.type(), rejection.reason()));
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private ClusterVariableRecord applyUpdate(
      final ClusterVariableRecord stored, final ClusterVariableRecord command) {
    stored.setValue(command.getValueBuffer());
    stored.setMetadata(command.getMetadataBuffer());
    return stored;
  }

  private Either<Rejection, ClusterVariableRecord> isAuthorized(
      final ClusterVariableRecord record, final TypedRecord<ClusterVariableRecord> command) {
    final ClusterVariableRecord clusterVariableRecord = command.getValue();
    return switch (clusterVariableRecord.getScope()) {
      case GLOBAL -> checkPermission(command, record);
      case TENANT -> checkAuthorizationForTenantScope(command, record);
      default ->
      // should never happen as scope is validated earlier
      {
        LOGGER.warn(
            "The scope validation has not been performed correctly. A ticket should be created.");
        yield new Left<>(
            new Rejection(RejectionType.UNAUTHORIZED, "An unknown authorization issue occurred."));
      }
    };
  }

  private Either<Rejection, ClusterVariableRecord> checkAuthorizationForTenantScope(
      final TypedRecord<ClusterVariableRecord> command, final ClusterVariableRecord record) {
    return cslCheck
        .checkTenant(
            command,
            record.getTenantId(),
            record,
            new Rejection(
                RejectionType.NOT_FOUND,
                NOT_FOUND_FOR_TENANT_MESSAGE.formatted(
                    PermissionType.UPDATE,
                    AuthorizationResourceType.CLUSTER_VARIABLE,
                    record.getTenantId())))
        .flatMap(unused -> checkPermission(command, record));
  }

  private Either<Rejection, ClusterVariableRecord> checkPermission(
      final TypedRecord<ClusterVariableRecord> command, final ClusterVariableRecord record) {
    return cslCheck.check(
        command,
        RequiredAuthorization.of(
            b ->
                b.resourceType(
                        AuthzModelMapper.fromProtocol(AuthorizationResourceType.CLUSTER_VARIABLE))
                    .permissionType(AuthzModelMapper.fromProtocol(PermissionType.UPDATE))
                    .resourceId(record.getName())),
        record,
        AuthorizationRejectionMapper.forbidden(
            PermissionType.UPDATE, AuthorizationResourceType.CLUSTER_VARIABLE));
  }
}
