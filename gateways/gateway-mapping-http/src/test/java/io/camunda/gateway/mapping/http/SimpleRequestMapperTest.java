/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationStartInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationTerminateInstruction;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class SimpleRequestMapperTest {

  @Nested
  class ProcessInstanceCreation {

    @Test
    void shouldRejectWhenNeitherIdNorKeyIsSet() {
      // given
      final var request = new ProcessInstanceCreationInstruction();

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail())
          .isEqualTo("At least one of [processDefinitionId, processDefinitionKey] is required.");
    }

    @Test
    void shouldRejectWhenBothIdAndKeyAreSet() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionId("process-id")
              .processDefinitionKey("123");

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail())
          .isEqualTo("Only one of [processDefinitionId, processDefinitionKey] is allowed.");
    }

    @Test
    void shouldRejectVersionWhenUsingKey() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .processDefinitionVersion(1);

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail())
          .isEqualTo("processDefinitionVersion can only be set when using processDefinitionId.");
    }

    @Test
    void shouldValidateProcessDefinitionKeyFormat() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("abc")
              .tenantId("<default>")
              .awaitCompletion(false);

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail())
          .isEqualTo(
              "The provided processDefinitionKey 'abc' is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?.");
    }

    @Test
    void shouldMapByKeyAndFailTenantWithMultiTenancyDisabled() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .tenantId("tenant-a")
              .startInstructions(
                  List.of(new ProcessInstanceCreationStartInstruction().elementId("start-element")))
              .runtimeInstructions(
                  List.of(
                      new ProcessInstanceCreationTerminateInstruction()
                          .type("TERMINATE_PROCESS_INSTANCE")
                          .afterElementId("after-element")));

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail())
          .isEqualTo(
              "Expected to handle request Create Process Instance with tenant identifier 'tenant-a', but multi-tenancy is disabled");
    }

    @Test
    void shouldMapByKeyAndFailMissingTenantWithMultiTenancyEnabled() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .startInstructions(
                  List.of(new ProcessInstanceCreationStartInstruction().elementId("start-element")))
              .runtimeInstructions(
                  List.of(
                      new ProcessInstanceCreationTerminateInstruction()
                          .type("TERMINATE_PROCESS_INSTANCE")
                          .afterElementId("after-element")));

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, true);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail())
          .isEqualTo(
              "Expected to handle request Create Process Instance with multi-tenancy enabled, but no tenant identifier was provided.");
    }

    @Test
    void shouldMapById() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionId("process-id")
              .processDefinitionVersion(2);

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isRight()).isTrue();
      final var mapped = result.get();
      assertThat(mapped.bpmnProcessId()).isEqualTo("process-id");
      assertThat(mapped.version()).isEqualTo(2);
      assertThat(mapped.processDefinitionKey()).isEqualTo(-1L);
    }

    @Test
    void shouldMapInstructions() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .startInstructions(
                  List.of(new ProcessInstanceCreationStartInstruction().elementId("start-element")))
              .runtimeInstructions(
                  List.of(
                      new ProcessInstanceCreationTerminateInstruction()
                          .type("TERMINATE_PROCESS_INSTANCE")
                          .afterElementId("after-element")));

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isRight()).isTrue();
      final var mapped = result.get();
      assertThat(mapped.processDefinitionKey()).isEqualTo(123L);
      assertThat(mapped.startInstructions())
          .hasSize(1)
          .first()
          .satisfies(i -> assertThat(i.getElementId()).isEqualTo("start-element"));
      assertThat(mapped.runtimeInstructions())
          .hasSize(1)
          .first()
          .satisfies(
              i -> {
                assertThat(i.getAfterElementId()).isEqualTo("after-element");
              });
    }

    @Test
    void shouldMapBusinessId() {
      // given
      final var businessId = "order-12345";
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .businessId(businessId);

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isRight()).isTrue();
      final var mapped = result.get();
      assertThat(mapped.processDefinitionKey()).isEqualTo(123L);
      assertThat(mapped.businessId()).isEqualTo(businessId);
    }
  }
}
