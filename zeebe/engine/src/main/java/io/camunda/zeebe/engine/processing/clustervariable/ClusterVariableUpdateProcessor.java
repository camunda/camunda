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
        .flatMap(stored -> applyUpdateWithSecretReferences(stored, commandRecord))
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

  /**
   * For a SECRET_REFERENCE-stored variable, scans the command's value for secret references and
   * sets them on the command record before applying the update; a non-SECRET_REFERENCE variable
   * skips the scan. Kind is immutable-from-stored on update (the command may omit it), so scanning
   * keys off the stored kind, not the (possibly absent) command kind. Only ever called on the
   * origin partition ({@link #processNewCommand}), on the command record: the resulting refs then
   * ride the distributed command, and the receiver's {@link #processDistributedCommand} copies them
   * onto its stored record via {@link #applyUpdate} without re-scanning.
   */
  private Either<Rejection, ClusterVariableRecord> applyUpdateWithSecretReferences(
      final ClusterVariableRecord stored, final ClusterVariableRecord command) {
    if (stored.getKind() != ClusterVariableKind.SECRET_REFERENCE) {
      return Either.right(applyUpdate(stored, command));
    }
    return secretReferenceScanner
        .scan(command.getValueBuffer())
        .map(
            references -> {
              references.forEach(ref -> command.addSecretReference("", ref.name(), ref.pointer()));
              return applyUpdate(stored, command);
            });
  }

  private ClusterVariableRecord applyUpdate(
      final ClusterVariableRecord stored, final ClusterVariableRecord command) {
    stored.setValue(command.getValueBuffer());
    stored.setMetadata(command.getMetadataBuffer());
    // reset-then-add so a value updated from secrets to none ends up empty rather than appended
    stored.copySecretReferencesFrom(command);
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
