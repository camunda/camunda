/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.state.usertask.UserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UserTaskListenerDoneProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private final UserTaskState userTaskState;

  public UserTaskListenerDoneProcessor(final UserTaskState userTaskState) {
    this.userTaskState = userTaskState;
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> record) {
    // find user task by key
    // validate command
    // invoke next listener
    // or, mark user task as created

    final long userTaskKey = record.getKey();
  }
}
