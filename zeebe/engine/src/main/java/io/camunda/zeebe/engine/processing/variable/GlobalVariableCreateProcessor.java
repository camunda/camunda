/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.protocol.impl.record.value.variable.GlobalVariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GlobalVariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class GlobalVariableCreateProcessor implements TypedRecordProcessor<GlobalVariableRecord> {

  private final KeyGenerator keyGenerator;
  private final VariableBehavior variableBehavior;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public GlobalVariableCreateProcessor(
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<GlobalVariableRecord> record) {
    processGlobalVariable(record);
  }

  private void processGlobalVariable(final TypedRecord<GlobalVariableRecord> record) {
    final var value = record.getValue();
    final var authRequest =
        new AuthorizationRequest(
            record, AuthorizationResourceType.APPLICATION, PermissionType.CREATE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "update global variables for element", -1, "such element")
              : rejection.reason();
      writers.rejection().appendRejection(record, rejection.type(), errorMessage);
      writers.response().writeRejectionOnCommand(record, rejection.type(), errorMessage);
    }

    variableBehavior.mergeGlobalVariables(value.getScopeKey(), value.getVariablesBuffer());

    final long key = keyGenerator.nextKey();

    writers.response().writeEventOnCommand(key, GlobalVariableIntent.CREATED, value, record);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.DEPLOYMENT.getQueueId())
        .distribute(record);
  }
}
