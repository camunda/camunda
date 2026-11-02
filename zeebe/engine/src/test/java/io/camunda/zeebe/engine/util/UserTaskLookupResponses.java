/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.awaitility.Awaitility;

/**
 * Captures the accepted responses to {@code USER_TASK_LOOKUP} commands that the mocked {@link
 * CommandResponseWriter} of an {@code EngineRule} receives, by request id. Rejections are not
 * captured: assert on the rejection record on the log instead.
 *
 * <p>The looked-up task, its lifecycle state and its variables are only in the response; the {@code
 * FETCHED} event on the log carries just the request. So a test has to read the response to see
 * what a lookup returned.
 *
 * <p>Usage: call {@link #install(CommandResponseWriter)} once with {@code
 * engine.getCommandResponseWriter()}, send each lookup with its own request id, then {@link
 * #awaitResponse(long)}.
 */
public final class UserTaskLookupResponses {

  private final Map<Long, UserTaskLookupRecord> responses = new ConcurrentHashMap<>();
  // set and read on the single thread that sends the responses: the record type comes first, then
  // the value, then the request id
  private boolean pendingRejection;
  private UserTaskLookupRecord pendingValue;

  public void install(final CommandResponseWriter mockResponseWriter) {
    doAnswer(
            invocation -> {
              pendingRejection = invocation.getArgument(0) == RecordType.COMMAND_REJECTION;
              return mockResponseWriter;
            })
        .when(mockResponseWriter)
        .recordType(any());
    doAnswer(
            invocation -> {
              // no copy needed: the engine decodes every response into a new record instance
              pendingValue =
                  !pendingRejection
                          && invocation.getArgument(0) instanceof final UserTaskLookupRecord record
                      ? record
                      : null;
              return mockResponseWriter;
            })
        .when(mockResponseWriter)
        .valueWriter(any());
    doAnswer(
            invocation -> {
              if (pendingValue != null) {
                responses.put(invocation.getArgument(1), pendingValue);
                pendingValue = null;
              }
              return null;
            })
        .when(mockResponseWriter)
        .tryWriteResponse(anyInt(), anyLong());
  }

  public UserTaskLookupRecord awaitResponse(final long requestId) {
    Awaitility.await("until the lookup response for request %d is written".formatted(requestId))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(responses).containsKey(requestId));
    return responses.get(requestId);
  }
}
