/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clustervariableresolver.ClusterVariableResolverRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableResolverIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processor for handling cluster variable resolution commands. Evaluates FEEL expressions
 * containing references to cluster variables and returns the resolved values.
 */
public class ClusterVariableResolverProcessor
    implements TypedRecordProcessor<ClusterVariableResolverRecord> {

  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;
  private final ClusterVariableBehavior clusterVariableBehavior;
  private final KeyGenerator keyGenerator;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public ClusterVariableResolverProcessor(
      final Writers writers,
      final ClusterVariableBehavior clusterVariableBehavior,
      final KeyGenerator keyGenerator,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    stateWriter = writers.state();
    this.clusterVariableBehavior = clusterVariableBehavior;
    this.keyGenerator = keyGenerator;
    this.authorizationCheckBehavior = authorizationCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterVariableResolverRecord> command) {
    final var record = command.getValue();
    final var tenantId = record.getTenantId();

    clusterVariableBehavior
        .resolveReference(record.getReference(), tenantId)
        .ifRightOrLeft(
            resolvedValue -> acceptCommand(command, resolvedValue),
            rejection -> rejectCommand(command, rejection));
  }

  private void rejectCommand(
      final TypedRecord<ClusterVariableResolverRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
  }

  private void acceptCommand(
      final TypedRecord<ClusterVariableResolverRecord> command, final String resolvedValue) {
    final var key = keyGenerator.nextKey();
    final var responseRecord = new ClusterVariableResolverRecord();
    responseRecord.setTenantId(command.getValue().getTenantId()).setResolvedValue(resolvedValue);
    stateWriter.appendFollowUpEvent(key, ClusterVariableResolverIntent.RESOLVED, responseRecord);
    responseWriter.writeEventOnCommand(
        key, ClusterVariableResolverIntent.RESOLVED, responseRecord, command);
  }
}
