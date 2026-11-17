/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

final class UserTaskLookupRecordTest {

  @Test
  void shouldKeepUserTaskWhenTheSourceBufferIsReused() {
    // given
    // a user task read from a buffer, like a state read: its values are views into that buffer
    final var buffer = new ExpandableArrayBuffer();
    final var userTask = read(buffer, new UserTaskRecord().setAssignee("first"));
    final var lookup = new UserTaskLookupRecord();

    // when
    lookup.setUserTask(userTask);
    // the next state read reuses the buffer
    read(buffer, new UserTaskRecord().setAssignee("other"));

    // then
    assertThat(lookup.getUserTask().getAssignee()).isEqualTo("first");
  }

  @Test
  void shouldSetUserTaskWithTheSameValues() {
    // given
    final var userTask =
        new UserTaskRecord()
            .setUserTaskKey(1)
            .setAssignee("demo")
            .setElementId("task")
            .setTenantId("tenant");
    final var lookup = new UserTaskLookupRecord();

    // when
    lookup.setUserTask(userTask);

    // then
    assertThat(lookup.getUserTask()).isNotSameAs(userTask).isEqualTo(userTask);
  }

  private static UserTaskRecord read(
      final ExpandableArrayBuffer buffer, final UserTaskRecord stored) {
    stored.write(buffer, 0);
    final var userTask = new UserTaskRecord();
    userTask.wrap(buffer, 0, stored.getLength());
    return userTask;
  }
}
