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

public class ClusterVariableCreateProcessor
    implements DistributedTypedRecordProcessor<ClusterVariableRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClusterVariableCreateProcessor.class);
  private static final String FORBIDDEN_FOR_TENANT_MESSAGE =
      "Expected to perform operation '%s' on resource '%s' for tenant '%s', but user is not assigned to this tenant";

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final CslAuthorizationCheck cslCheck;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ClusterVariableRecordValidator clusterVariableRecordValidator;
  private final ClusterVariableSecretReferenceScanner secretReferenceScanner;

  public ClusterVariableCreateProcessor(
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
    final ClusterVariableRecord clusterVariableRecord = command.getValue();
    clusterVariableRecordValidator
        .validateName(clusterVariableRecord)
        .flatMap(clusterVariableRecordValidator::ensureValidScope)
        .flatMap(record -> isAuthorized(record, command))
        .flatMap(clusterVariableRecordValidator::validateUniqueness)
        .flatMap(this::applySecretReferences)
        .ifRightOrLeft(
            record -> {
              final var key = keyGenerator.nextKey();
              writers
                  .state()
                  .appendFollowUpEvent(key, ClusterVariableIntent.CREATED, clusterVariableRecord);
              writers
                  .response()
                  .writeAcceptedResponseOnCommand(
                      key, ClusterVariableIntent.CREATED, clusterVariableRecord, command);
              commandDistributionBehavior
                  .withKey(key)
                  .inQueue(clusterVariableRecord.getName())
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
    final var record = command.getValue();
    clusterVariableRecordValidator
        .validateUniqueness(record)
        .ifRightOrLeft(
            clusterVariableRecord ->
                writers
                    .state()
                    .appendFollowUpEvent(command.getKey(), ClusterVariableIntent.CREATED, record),
            rejection ->
                writers.rejection().appendRejection(command, rejection.type(), rejection.reason()));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * For a SECRET_REFERENCE-kind record, scans its value for secret references and sets them on the
   * record; a non-SECRET_REFERENCE record passes through unchanged. Only ever called on the origin
   * partition ({@link #processNewCommand}): the resulting refs are set on the same record that is
   * appended, returned in the accepted response, and distributed, so a receiver's {@link
   * #processDistributedCommand} carries them without re-scanning.
   */
  private Either<Rejection, ClusterVariableRecord> applySecretReferences(
      final ClusterVariableRecord record) {
    if (record.getKind() != ClusterVariableKind.SECRET_REFERENCE) {
      return Either.right(record);
    }
    return secretReferenceScanner
        .scan(record.getValueBuffer())
        .map(
            references -> {
              references.forEach(
                  ref ->
                      record.addSecretReference(
                          SecretStoreRegistry.DEFAULT_STORE_ID, ref.name(), ref.pointer()));
              return record;
            });
  }

  private Either<Rejection, ClusterVariableRecord> isAuthorized(
      final ClusterVariableRecord record, final TypedRecord<ClusterVariableRecord> command) {
    return switch (record.getScope()) {
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
                    .permissionType(AuthzModelMapper.fromProtocol(PermissionType.CREATE))
                    .resourceId(record.getName())),
        record,
        AuthorizationRejectionMapper.forbidden(
            PermissionType.CREATE, AuthorizationResourceType.CLUSTER_VARIABLE),
        record.getTenantId(),
        new Rejection(
            RejectionType.FORBIDDEN,
            FORBIDDEN_FOR_TENANT_MESSAGE.formatted(
                PermissionType.CREATE,
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
                    .permissionType(AuthzModelMapper.fromProtocol(PermissionType.CREATE))
                    .resourceId(record.getName())),
        record,
        AuthorizationRejectionMapper.forbidden(
            PermissionType.CREATE, AuthorizationResourceType.CLUSTER_VARIABLE));
  }
}
