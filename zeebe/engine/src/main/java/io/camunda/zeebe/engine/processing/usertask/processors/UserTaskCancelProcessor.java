/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UserTaskCancelProcessor implements UserTaskCommandProcessor {

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    throw new UnsupportedOperationException(
        "UserTaskCancelProcessor.onFinalizeCommand is not yet implemented. "
            + "It should be covered by: https://github.com/camunda/camunda/issues/28570");
  }
}
