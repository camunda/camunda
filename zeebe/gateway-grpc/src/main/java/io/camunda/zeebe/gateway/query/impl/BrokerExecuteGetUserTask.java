/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.query.impl;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import org.agrona.DirectBuffer;

public class BrokerExecuteGetUserTask extends BrokerExecuteGetRequest<UserTaskRecordValue> {

  @Override
  protected UserTaskRecordValue toResponseDto(final DirectBuffer buffer) {
    final var userTask = new UserTaskRecord();
    userTask.wrap(buffer);
    return userTask;
  }
}
