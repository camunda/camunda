/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public class ClusterVariableDeleteProcessor
    implements DistributedTypedRecordProcessor<ClusterVariableRecord> {

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ClusterVariableRecordValidator clusterVariableRecordValidator;

  public ClusterVariableDeleteProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ClusterVariableState clusterVariableState) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
    clusterVariableRecordValidator = new ClusterVariableRecordValidator(clusterVariableState);
  }

  @Override
  public void processNewCommand(final TypedRecord<ClusterVariableRecord> command) {
    final ClusterVariableRecord clusterVariableRecord = command.getValue();
    clusterVariableRecordValidator
        .ensureValidScope(clusterVariableRecord)
        .flatMap(clusterVariableRecordValidator::validateExistence)
        .ifRightOrLeft(
            record -> {
              final long key = keyGenerator.nextKey();
              writers
                  .state()
                  .appendFollowUpEvent(key, ClusterVariableIntent.DELETED, clusterVariableRecord);
              writers
                  .response()
                  .writeEventOnCommand(
                      key, ClusterVariableIntent.DELETED, clusterVariableRecord, command);
              commandDistributionBehavior
                  .withKey(key)
                  .inQueue(clusterVariableRecord.getName())
                  .distribute(command);
            },
            rejection -> {
              writers.rejection().appendRejection(command, rejection.type(), rejection.reason());
              writers
                  .response()
                  .writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClusterVariableRecord> command) {
    final var clusterVariableRecord = command.getValue();
    clusterVariableRecordValidator
        .validateExistence(clusterVariableRecord)
        .ifRightOrLeft(
            record ->
                writers
                    .state()
                    .appendFollowUpEvent(command.getKey(), ClusterVariableIntent.DELETED, record),
            rejection ->
                writers.rejection().appendRejection(command, rejection.type(), rejection.reason()));
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
