/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.validator.GlobalListenerRequestValidator;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@ExtendWith(MockitoExtension.class)
class GlobalListenerMapperTest {

  @Mock private GlobalListenerRequestValidator requestValidator;

  private GlobalListenerMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GlobalListenerMapper(requestValidator);
  }

  @Test
  void shouldMapValidGetRequest() {
    // given
    final String id = "listener-id-123";
    when(requestValidator.validateGetRequest(id)).thenReturn(Optional.empty());

    // when
    final var result = mapper.toGlobalTaskListenerGetRequest(id);

    // then
    assertThat(result.isRight()).isTrue();
    final var record = result.get();
    assertThat(record.getId()).isEqualTo(id);
    assertThat(record.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.TASK_LISTENER);
  }

  @Test
  void shouldRejectInvalidGetRequest() {
    // given
    final String invalidId = "$invalid-id";
    final var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setDetail(
        "The provided id contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.");
    when(requestValidator.validateGetRequest(invalidId)).thenReturn(Optional.of(problemDetail));

    // when
    final var result = mapper.toGlobalTaskListenerGetRequest(invalidId);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getStatus()).isEqualTo(400);
    assertThat(result.getLeft().getDetail()).contains("illegal characters");
  }

  @Test
  void shouldMapGetRequestWithSpecialCharacters() {
    // given
    final String id = "listener.id-123_test@domain.com";
    when(requestValidator.validateGetRequest(id)).thenReturn(Optional.empty());

    // when
    final var result = mapper.toGlobalTaskListenerGetRequest(id);

    // then
    assertThat(result.isRight()).isTrue();
    final var record = result.get();
    assertThat(record.getId()).isEqualTo(id);
  }

  @Test
  void shouldHandleEmptyIdValidation() {
    // given
    final String emptyId = "";
    final var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setDetail("The attribute id must not be empty.");
    when(requestValidator.validateGetRequest(emptyId)).thenReturn(Optional.of(problemDetail));

    // when
    final var result = mapper.toGlobalTaskListenerGetRequest(emptyId);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getStatus()).isEqualTo(400);
    assertThat(result.getLeft().getDetail()).contains("must not be empty");
  }
}
