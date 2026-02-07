/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

final class UserTaskCommandHelper {
  private UserTaskCommandHelper() {}

  /**
   * Enriches the command with fields from the persisted user task record. This ensures that if any
   * subsequent validation fails and a rejection is written, it will have the proper context to be
   * exported for audit log purposes.
   */
  static Either<Rejection, UserTaskRecord> enrichCommandForRejection(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord persistedUserTask) {
    command.getValue().setTenantId(persistedUserTask.getTenantId());
    command.getValue().setRootProcessInstanceKey(persistedUserTask.getRootProcessInstanceKey());
    return Either.right(persistedUserTask);
  }
}
