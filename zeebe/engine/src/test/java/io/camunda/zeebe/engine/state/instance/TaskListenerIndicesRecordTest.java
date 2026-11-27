/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class TaskListenerIndicesRecordTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldSerializeIndicesWithTheirKeysInDeclarationOrder() throws Exception {
    // given
    final var record = new TaskListenerIndicesRecord();
    incrementTimes(record, ZeebeTaskListenerEventType.creating, 1);
    incrementTimes(record, ZeebeTaskListenerEventType.assigning, 2);
    incrementTimes(record, ZeebeTaskListenerEventType.updating, 3);
    incrementTimes(record, ZeebeTaskListenerEventType.completing, 4);
    incrementTimes(record, ZeebeTaskListenerEventType.canceling, 5);

    // when
    final var serialized = serialize(record);

    // then - the persisted key names must not change: a renamed key is no longer matched when
    // reading existing state, so the stored index would silently reset to its default
    assertThat(toMap(serialized))
        .containsExactly(
            Map.entry("creatingTaskListenerIndex", 1),
            Map.entry("assigningTaskListenerIndex", 2),
            Map.entry("updatingTaskListenerIndex", 3),
            Map.entry("completingTaskListenerIndex", 4),
            Map.entry("cancelingTaskListenerIndex", 5));
  }

  private static void incrementTimes(
      final TaskListenerIndicesRecord record,
      final ZeebeTaskListenerEventType eventType,
      final int times) {
    for (int i = 0; i < times; i++) {
      record.incrementTaskListenerIndex(eventType);
    }
  }

  private static UnsafeBuffer serialize(final TaskListenerIndicesRecord record) {
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);
    return buffer;
  }

  private static Map<String, Integer> toMap(final UnsafeBuffer serialized) throws Exception {
    return MAPPER.readValue(
        MsgPackConverter.convertToJson(serialized),
        new TypeReference<LinkedHashMap<String, Integer>>() {});
  }
}
