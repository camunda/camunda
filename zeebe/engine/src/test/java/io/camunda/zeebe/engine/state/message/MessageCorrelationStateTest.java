/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class MessageCorrelationStateTest {
  private MutableProcessingState processingState;

  private MutableMessageCorrelationState state;

  @BeforeEach
  public void setUp() {
    state = processingState.getMessageCorrelationState();
  }

  @Test
  void shouldPutMessageCorrelation() {
    // given
    final var messageKey = 1L;
    final var requestId = 2L;
    final var requestStreamId = 3;

    // when
    state.putMessageCorrelation(messageKey, requestId, requestStreamId);

    // then
    assertThat(state.getRequestData(messageKey))
        .extracting(RequestData::getRequestId, RequestData::getRequestStreamId)
        .containsExactly(requestId, requestStreamId);
  }

  @Test
  void shouldNotPutDuplicateKey() {
    // given
    final var messageKey = 1L;
    final var requestId = 2L;
    final var requestStreamId = 3;
    state.putMessageCorrelation(messageKey, requestId, requestStreamId);

    // when - then
    final var exception =
        assertThatExceptionOfType(ZeebeDbInconsistentException.class)
            .as("Expected to insert new element, but element with key '1' already exists")
            .isThrownBy(() -> state.putMessageCorrelation(messageKey, requestId, requestStreamId))
            .actual();
    assertThat(exception)
        .hasMessage("Key DbLong{1} in ColumnFamily MESSAGE_CORRELATION already exists");
  }

  @Test
  void shouldCheckIfMessageCorrelationExists() {
    // given
    final var messageKey = 1L;
    final var requestId = 2L;
    final var requestStreamId = 3;

    // when
    state.putMessageCorrelation(messageKey, requestId, requestStreamId);

    // then
    assertThat(state.existsRequestDataForMessageKey(messageKey)).isTrue();
  }

  @Test
  void shouldCheckIfNoMessageCorrelationExists() {
    // when - then
    assertThat(state.existsRequestDataForMessageKey(1)).isFalse();
  }

  @Test
  void shouldRemoveMessageCorrelation() {
    // given
    final var messageKey = 1L;
    final var requestId = 2L;
    final var requestStreamId = 3;
    state.putMessageCorrelation(messageKey, requestId, requestStreamId);

    // when
    state.removeMessageCorrelation(messageKey);

    // then
    assertThat(state.existsRequestDataForMessageKey(messageKey)).isFalse();
  }

  @Test
  void shouldNotRemoveNonExistingMessageCorrelation() {
    // when - then
    final var exception =
        assertThatExceptionOfType(ZeebeDbInconsistentException.class)
            .as("Expected to delete existing element, but no element with key '1' found")
            .isThrownBy(() -> state.removeMessageCorrelation(1))
            .actual();
    assertThat(exception)
        .hasMessage("Key DbLong{1} in ColumnFamily MESSAGE_CORRELATION does not exist");
  }

  @Test
  void shouldReturnNullWhenGettingNonExistingKey() {
    // when - then
    assertThat(state.getRequestData(1)).isNull();
  }

  @Test
  void shouldGetCopyOfRequestData() {
    // given
    final var key1 = 1L;
    final var key2 = 2L;
    state.putMessageCorrelation(key1, 1L, 1);
    state.putMessageCorrelation(key2, 1L, 1);

    // when
    final var requestData1 = state.getRequestData(key1);
    final var requestData2 = state.getRequestData(key2);

    // then
    assertThat(requestData1).isNotSameAs(requestData2);
  }
}
