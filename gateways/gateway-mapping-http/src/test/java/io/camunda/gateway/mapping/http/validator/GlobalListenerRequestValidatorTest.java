/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.CreateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.UpdateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.IdentifierValidator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalListenerRequestValidatorTest {

  private static final Pattern ID_PATTERN =
      Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  private GlobalListenerRequestValidator validator;

  @BeforeEach
  void setUp() {
    final var identifierValidator = new IdentifierValidator(ID_PATTERN, ID_PATTERN);
    validator = new GlobalListenerRequestValidator(identifierValidator);
  }

  // --- Task listener create ---

  @Test
  void shouldAcceptValidTaskListenerCreateRequest() {
    // given
    final var request =
        new CreateGlobalTaskListenerRequest()
            .id("my-listener")
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.CREATING));

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectTaskListenerCreateWithMissingId() {
    // given
    final var request =
        new CreateGlobalTaskListenerRequest()
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.CREATING));

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  @Test
  void shouldRejectTaskListenerCreateWithInvalidId() {
    // given
    final var request =
        new CreateGlobalTaskListenerRequest()
            .id("$invalid!")
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.CREATING));

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  @Test
  void shouldRejectTaskListenerCreateWithMissingType() {
    // given
    final var request =
        new CreateGlobalTaskListenerRequest()
            .id("my-listener")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.CREATING));

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("type");
  }

  @Test
  void shouldRejectTaskListenerCreateWithEmptyEventTypes() {
    // given
    final var request =
        new CreateGlobalTaskListenerRequest()
            .id("my-listener")
            .type("job-type")
            .eventTypes(List.of());

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("eventTypes");
  }

  @Test
  void shouldRejectTaskListenerCreateWithNullEventTypes() {
    // given
    final var request =
        new CreateGlobalTaskListenerRequest().id("my-listener").type("job-type");

    // when
    final var result = validator.validateCreateRequest(request);

    // then
    assertThat(result).isPresent();
  }

  // --- Task listener update ---

  @Test
  void shouldAcceptValidTaskListenerUpdateRequest() {
    // given
    final var request =
        new UpdateGlobalTaskListenerRequest()
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.CREATING));

    // when
    final var result = validator.validateUpdateRequest("my-listener", request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectTaskListenerUpdateWithInvalidId() {
    // given
    final var request =
        new UpdateGlobalTaskListenerRequest()
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.CREATING));

    // when
    final var result = validator.validateUpdateRequest("$invalid", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  // --- Execution listener create ---

  @Test
  void shouldAcceptValidExecutionListenerCreateRequest() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest()
            .id("my-exec-listener")
            .type("job-type")
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldAcceptExecutionListenerCreateWithMultipleEventTypes() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest()
            .id("my-exec-listener")
            .type("job-type")
            .eventTypes(
                List.of(
                    GlobalExecutionListenerEventTypeEnum.START,
                    GlobalExecutionListenerEventTypeEnum.END));

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectExecutionListenerCreateWithMissingId() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest()
            .type("job-type")
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  @Test
  void shouldRejectExecutionListenerCreateWithInvalidId() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest()
            .id("$invalid!")
            .type("job-type")
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  @Test
  void shouldRejectExecutionListenerCreateWithMissingType() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest()
            .id("my-exec-listener")
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("type");
  }

  @Test
  void shouldRejectExecutionListenerCreateWithEmptyEventTypes() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest()
            .id("my-exec-listener")
            .type("job-type")
            .eventTypes(List.of());

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("eventTypes");
  }

  @Test
  void shouldRejectExecutionListenerCreateWithNullEventTypes() {
    // given
    final var request =
        new CreateGlobalExecutionListenerRequest().id("my-exec-listener").type("job-type");

    // when
    final var result = validator.validateExecutionListenerCreateRequest(request);

    // then
    assertThat(result).isPresent();
  }

  // --- Execution listener update ---

  @Test
  void shouldAcceptValidExecutionListenerUpdateRequest() {
    // given
    final var request =
        new UpdateGlobalExecutionListenerRequest()
            .type("job-type")
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

    // when
    final var result = validator.validateExecutionListenerUpdateRequest("my-listener", request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectExecutionListenerUpdateWithInvalidId() {
    // given
    final var request =
        new UpdateGlobalExecutionListenerRequest()
            .type("job-type")
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

    // when
    final var result = validator.validateExecutionListenerUpdateRequest("$invalid", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  @Test
  void shouldRejectExecutionListenerUpdateWithMissingType() {
    // given
    final var request =
        new UpdateGlobalExecutionListenerRequest()
            .eventTypes(List.of(GlobalExecutionListenerEventTypeEnum.END));

    // when
    final var result = validator.validateExecutionListenerUpdateRequest("my-listener", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("type");
  }

  @Test
  void shouldRejectExecutionListenerUpdateWithEmptyEventTypes() {
    // given
    final var request =
        new UpdateGlobalExecutionListenerRequest().type("job-type").eventTypes(List.of());

    // when
    final var result = validator.validateExecutionListenerUpdateRequest("my-listener", request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("eventTypes");
  }

  // --- Get / Delete validators (shared for both types) ---

  @Test
  void shouldAcceptValidGetRequest() {
    final var result = validator.validateGetRequest("my-listener");
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectGetRequestWithInvalidId() {
    final var result = validator.validateGetRequest("$invalid!");
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }

  @Test
  void shouldAcceptValidDeleteRequest() {
    final var result = validator.validateDeleteRequest("my-listener");
    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectDeleteRequestWithInvalidId() {
    final var result = validator.validateDeleteRequest("$invalid!");
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("id");
  }
}
