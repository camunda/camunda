/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.secretstore.SecretStoreRegistry;
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
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
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
  private final ClusterVariableSecretReferenceScanner secretReferenceScanner;

  public ClusterVariableUpdateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ClusterVariableRecordValidator clusterVariableRecordValidator,
      final ClusterVariableSecretReferenceScanner secretReferenceScanner) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.cslCheck = cslCheck;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.clusterVariableRecordValidator = clusterVariableRecordValidator;
    this.secretReferenceScanner = secretReferenceScanner;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClusterVariableRecord> command) {
    final ClusterVariableRecord commandRecord = command.getValue();
    clusterVariableRecordValidator
        .ensureValidScope(commandRecord)
        .flatMap(clusterVariableRecordValidator::loadExisting)
        .flatMap(stored -> isAuthorized(stored, command))
        .flatMap(stored -> applyUpdateWithSecretReferences(stored, commandRecord))
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
        .validateExistence(commandRecord)
        .ifRightOrLeft(
            record ->
                writers
                    .state()
                    .appendFollowUpEvent(command.getKey(), ClusterVariableIntent.UPDATED, record),
            rejection ->
                writers.rejection().appendRejection(command, rejection.type(), rejection.reason()));
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Kind is immutable-from-stored on update (the command may omit or misreport it), so this pins
   * the stored kind onto the command record first. For a SECRET_REFERENCE-stored variable, it then
   * scans the command's (new) value for secret references and sets them on the command record; a
   * non-SECRET_REFERENCE variable skips the scan. Only ever called on the origin partition ({@link
   * #processNewCommand}): the command record already carries the new value/metadata, and now kind
   * and secretReferences too, making it a complete stand-in for the merged record. That same
   * command then rides the distribution, so the receiver's {@link #processDistributedCommand} only
   * needs to confirm the variable still exists and can append the command record as-is.
   */
  private Either<Rejection, ClusterVariableRecord> applyUpdateWithSecretReferences(
      final ClusterVariableRecord stored, final ClusterVariableRecord command) {
    command.setKind(stored.getKind());
    if (stored.getKind() != ClusterVariableKind.SECRET_REFERENCE) {
      return Either.right(command);
    }
    return secretReferenceScanner
        .scan(command.getValueBuffer())
        .map(
            references -> {
              references.forEach(
                  ref ->
                      command.addSecretReference(
                          SecretStoreRegistry.DEFAULT_STORE_ID, ref.name(), ref.pointer()));
              return command;
            });
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
    return cslCheck.checkAuthorizationAndTenant(
        command,
        RequiredAuthorization.of(
            b ->
                b.resourceType(
                        AuthzModelMapper.fromProtocol(AuthorizationResourceType.CLUSTER_VARIABLE))
                    .permissionType(AuthzModelMapper.fromProtocol(PermissionType.UPDATE))
                    .resourceId(record.getName())),
        record,
        AuthorizationRejectionMapper.forbidden(
            PermissionType.UPDATE, AuthorizationResourceType.CLUSTER_VARIABLE),
        record.getTenantId(),
        new Rejection(
            RejectionType.NOT_FOUND,
            NOT_FOUND_FOR_TENANT_MESSAGE.formatted(
                PermissionType.UPDATE,
                AuthorizationResourceType.CLUSTER_VARIABLE,
                record.getTenantId())));
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
