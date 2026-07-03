/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.util.Either;

/**
 * A reusable validation check (POC #56552) that rejects commands targeting a suspended process
 * instance's "no side effects while paused" guarantee — e.g. job completion/failure and message
 * correlation. Modeled on {@link BannedInstanceCommandCheck}.
 */
public class SuspendedInstanceCommandCheck {

  private static final String SUSPENDED_MESSAGE =
      "Expected to process a command for process instance '%d', but it is suspended";

  private final SuspensionState suspensionState;

  public SuspendedInstanceCommandCheck(final SuspensionState suspensionState) {
    this.suspensionState = suspensionState;
  }

  public <T extends ProcessInstanceRelated> Either<Rejection, T> check(final T record) {
    final long processInstanceKey = record.getProcessInstanceKey();

    if (suspensionState.isSuspended(processInstanceKey)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE, SUSPENDED_MESSAGE.formatted(processInstanceKey)));
    }

    return Either.right(record);
  }
}
