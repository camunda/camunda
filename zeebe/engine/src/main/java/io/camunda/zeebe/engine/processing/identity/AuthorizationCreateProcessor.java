/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class AuthorizationCreateProcessor implements TypedRecordProcessor<AuthorizationRecord> {
  private final AuthorizationRecord newAuthorizationRecord = new AuthorizationRecord();

  private final AuthorizationState authorizationState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public AuthorizationCreateProcessor(
      final ProcessingState processingState, final Writers writers) {
    authorizationState = processingState.getAuthorizationState();
    stateWriter = writers.state();
    keyGenerator = ((MutableProcessingState) processingState).getKeyGenerator();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<AuthorizationRecord> command) {
    final var authorizationToCreate = command.getValue();

    // TODO check permissions of command.getAuthorizations().get("user")

    final var authorization =
        authorizationState.getAuthorization(
            authorizationToCreate.getOwnerKey(),
            authorizationToCreate.getOwnerType(),
            authorizationToCreate.getResourceKey(),
            authorizationToCreate.getResourceType());

    if (authorization != null) {
      rejectionWriter.appendRejection(
          command, RejectionType.ALREADY_EXISTS, "authorization already exists");
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.ALREADY_EXISTS, "authorization already exists");
      return;
    }

    final var key = keyGenerator.nextKey();

    newAuthorizationRecord.reset();
    newAuthorizationRecord.wrap(command.getValue());
    newAuthorizationRecord.setAuthorizationKey(key);

    stateWriter.appendFollowUpEvent(key, AuthorizationIntent.CREATED, newAuthorizationRecord);
    responseWriter.writeEventOnCommand(
        key, AuthorizationIntent.CREATED, newAuthorizationRecord, command);
  }
}
