/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Responds with the current time of this partition's clock. The time includes any modification
 * applied to the clock, so it is the pinned time when the clock is pinned.
 */
@NullMarked
public final class ClockFetchProcessor implements TypedRecordProcessor<ClockRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final StreamClock clock;
  private final CslAuthorizationCheck cslCheck;
  private final ClockRecord fetchedRecord = new ClockRecord();

  public ClockFetchProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final StreamClock clock,
      final CslAuthorizationCheck cslCheck) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.clock = clock;
    this.cslCheck = cslCheck;
  }

  @Override
  public void processRecord(final TypedRecord<ClockRecord> command) {
    final var isAuthorized =
        cslCheck.check(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.SYSTEM))
                        .permissionType(AuthzModelMapper.fromProtocol(PermissionType.READ))
                        .resourceId(AuthorizationScope.WILDCARD_CHAR)),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.READ, AuthorizationResourceType.SYSTEM));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    fetchedRecord.reset();
    fetchedRecord.setTime(clock.millis());
    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ClockIntent.FETCHED, fetchedRecord);
    responseWriter.writeAcceptedResponseOnCommand(
        eventKey, ClockIntent.FETCHED, fetchedRecord, command);
  }
}
