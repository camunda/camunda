/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.engine.Engine.ERROR_MESSAGE_BANNED_PI;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.util.Either;

/**
 * A reusable validation check that verifies whether a command is associated with a banned process
 * instance. This check should be applied to ProcessInstanceRelated commands after the associated
 * record has been retrieved from state.
 *
 * <p>This check is designed to be used with precondition validators for Job, UserTask, Incident,
 * and Variable commands, avoiding additional state reads by leveraging already-retrieved records.
 */
public class BannedInstanceCommandCheck {

  private final BannedInstanceState bannedInstanceState;

  public BannedInstanceCommandCheck(final BannedInstanceState bannedInstanceState) {
    this.bannedInstanceState = bannedInstanceState;
  }

  /**
   * Checks if the process instance associated with the given record is banned.
   *
   * @param record the record retrieved from state (must implement ProcessInstanceRelated)
   * @param <T> the record type, must extend ProcessInstanceRelated
   * @return Either.left with a rejection if the instance is banned, or Either.right with the record
   *     if not banned
   */
  public <T extends ProcessInstanceRelated> Either<Rejection, T> check(final T record) {
    final long processInstanceKey = record.getProcessInstanceKey();

    if (bannedInstanceState.isProcessInstanceBanned(processInstanceKey)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE, ERROR_MESSAGE_BANNED_PI.formatted(processInstanceKey)));
    }

    return Either.right(record);
  }
}
