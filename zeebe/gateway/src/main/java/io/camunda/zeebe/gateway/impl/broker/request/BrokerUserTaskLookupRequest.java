/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerUserTaskLookupRequest extends BrokerExecuteCommand<UserTaskLookupRecord> {

  private final UserTaskLookupRecord requestDto = new UserTaskLookupRecord();

  public BrokerUserTaskLookupRequest(final long userTaskKey, final List<String> fetchVariables) {
    super(ValueType.USER_TASK_LOOKUP, UserTaskLookupIntent.FETCH);
    requestDto.setFetchVariables(fetchVariables);
    request.setKey(userTaskKey);
  }

  @Override
  public UserTaskLookupRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected UserTaskLookupRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new UserTaskLookupRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
