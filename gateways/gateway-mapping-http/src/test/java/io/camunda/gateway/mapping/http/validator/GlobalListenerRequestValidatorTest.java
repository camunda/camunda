/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.security.validation.IdentifierValidator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class GlobalListenerRequestValidatorTest {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  private final GlobalListenerRequestValidator validator =
      new GlobalListenerRequestValidator(new IdentifierValidator(ID_PATTERN, ID_PATTERN));

  @Test
  void shouldAcceptValidCreateRequest() {
    // given
    final var request = createRequest("my-listener", "job-type", eventTypes());

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectCreateRequestWithInvalidId() {
    // given
    final var request = createRequest("$invalid", "job-type", eventTypes());

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("provided id");
  }

  @Test
  void shouldRejectCreateRequestWithInvalidType() {
    // given
    final var request = createRequest("my-listener", "not a job type", eventTypes());

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("provided type");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldRejectCreateRequestWithoutEventTypes(
      final List<GlobalTaskListenerEventTypeEnum> none) {
    // given
    final var request = createRequest("my-listener", "job-type", none);

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).isEqualTo("No eventTypes provided.");
  }

  @Test
  void shouldReportAllCreateViolationsTogether() {
    // given
    final var request = createRequest("$invalid", "not a job type", List.of());

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail())
        .contains("provided id")
        .contains("provided type")
        .contains("No eventTypes provided");
  }

  @Test
  void shouldAcceptValidUpdateRequest() {
    // given
    final var request = updateRequest("job-type", eventTypes());

    // when
    final var result = validator.validateUpdateRequest("my-listener", request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectUpdateRequestWithInvalidId() {
    // given
    final var request = updateRequest("job-type", eventTypes());

    // when
    final var result = validator.validateUpdateRequest("$invalid", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("provided id");
  }

  @Test
  void shouldRejectUpdateRequestWithInvalidType() {
    // given
    final var request = updateRequest("not a job type", eventTypes());

    // when
    final var result = validator.validateUpdateRequest("my-listener", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("provided type");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldRejectUpdateRequestWithoutEventTypes(
      final List<GlobalTaskListenerEventTypeEnum> none) {
    // given
    final var request = updateRequest("job-type", none);

    // when
    final var result = validator.validateUpdateRequest("my-listener", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).isEqualTo("No eventTypes provided.");
  }

  @Test
  void shouldAcceptValidIdOnGetAndDelete() {
    // when / then
    assertThat(validator.validateGetRequest("my-listener")).isEmpty();
    assertThat(validator.validateDeleteRequest("my-listener")).isEmpty();
  }

  @Test
  void shouldRejectInvalidIdOnGetAndDelete() {
    // when / then
    assertThat(validator.validateGetRequest("$invalid")).isPresent();
    assertThat(validator.validateDeleteRequest("$invalid")).isPresent();
  }

  private static List<GlobalTaskListenerEventTypeEnum> eventTypes() {
    return List.of(GlobalTaskListenerEventTypeEnum.CREATING);
  }

  private static CreateGlobalTaskListenerRequest createRequest(
      final String id, final String type, final List<GlobalTaskListenerEventTypeEnum> eventTypes) {
    return CreateGlobalTaskListenerRequest.Builder.create()
        .id(id)
        .type(type)
        .eventTypes(eventTypes)
        .build();
  }

  private static UpdateGlobalTaskListenerRequest updateRequest(
      final String type, final List<GlobalTaskListenerEventTypeEnum> eventTypes) {
    return UpdateGlobalTaskListenerRequest.Builder.create()
        .type(type)
        .eventTypes(eventTypes)
        .build();
  }
}
