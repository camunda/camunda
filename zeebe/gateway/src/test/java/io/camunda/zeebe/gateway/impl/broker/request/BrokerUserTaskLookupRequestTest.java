/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BrokerUserTaskLookupRequestTest {

  @Test
  void shouldSendFetchCommandToThePartitionOfTheUserTask() {
    // given
    final long userTaskKey = Protocol.encodePartitionId(3, 42L);

    // when
    final var request = new BrokerUserTaskLookupRequest(userTaskKey, List.of());

    // then
    assertThat(request.getValueType()).isEqualTo(ValueType.USER_TASK_LOOKUP);
    assertThat(request.getIntent()).isEqualTo(UserTaskLookupIntent.FETCH);
    assertThat(request.getKey()).isEqualTo(userTaskKey);
    assertThat(request.getPartitionId()).isEqualTo(3);
  }

  @Test
  void shouldSendOnlyTheRequestFields() {
    // given
    final var fetchVariables = List.of("recommendation", "applicant");

    // when
    final var request = new BrokerUserTaskLookupRequest(1L, fetchVariables);

    // then
    final var record = request.getRequestWriter();
    assertThat(record.getFetchVariables()).isEqualTo(fetchVariables);
    assertThat(record.getUserTask()).isEqualTo(new UserTaskRecord());
    assertThat(record.getLifecycleState()).isEqualTo(UserTaskLifecycleState.UNSPECIFIED);
  }

  @Test
  void shouldReadTheUserTaskAndLifecycleStateFromTheResponse() {
    // given
    final var request = new BrokerUserTaskLookupRequest(1L, List.of());
    final var response =
        new UserTaskLookupRecord()
            .setUserTask(
                new UserTaskRecord()
                    .setUserTaskKey(1L)
                    .setAssignee("previous-assignee")
                    .setVariables(asMsgPack("recommendation", "recommended")))
            .setLifecycleState(UserTaskLifecycleState.ASSIGNING);

    // when
    final var responseDto = request.toResponseDto(BufferUtil.createCopy(response));

    // then
    assertThat(responseDto.getLifecycleState()).isEqualTo(UserTaskLifecycleState.ASSIGNING);
    assertThat(responseDto.getUserTask().getUserTaskKey()).isEqualTo(1L);
    assertThat(responseDto.getUserTask().getAssignee()).isEqualTo("previous-assignee");
    assertThat(responseDto.getUserTask().getVariables())
        .isEqualTo(Map.of("recommendation", "recommended"));
  }
}
