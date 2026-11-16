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
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationStartInstruction;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    void shouldMapByIdAndVersionTag() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionId("process-id")
              .processDefinitionVersionTag("release-1")
              .variables(Map.of("foo", "bar"))
              .tenantId("<default>")
              .operationReference(123L)
              .startInstructions(
                  List.of(new ProcessInstanceCreationStartInstruction().elementId("start-element")))
              .runtimeInstructions(
                  List.of(
                      new ProcessInstanceCreationRuntimeInstruction()
                          .afterElementId("after-element")
                          .type("TERMINATE_PROCESS_INSTANCE")))
              .awaitCompletion(false)
              .fetchVariables(List.of("foo"))
              .requestTimeout(456L)
              .tags(Set.of("tag1"))
              .businessId("business-1");

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isRight()).isTrue();
      final var mapped = result.get();
      assertThat(mapped.bpmnProcessId()).isEqualTo("process-id");
      assertThat(mapped.versionTag()).isEqualTo("release-1");
      assertThat(mapped.version()).isEqualTo(-1);
      assertThat(mapped.processDefinitionKey()).isEqualTo(-1L);
      assertThat(mapped.variables()).isEqualTo(Map.of("foo", "bar"));
      assertThat(mapped.tenantId()).isEqualTo("<default>");
      assertThat(mapped.operationReference()).isEqualTo(123L);
      assertThat(mapped.startInstructions()).hasSize(1);
      assertThat(mapped.startInstructions().getFirst().getElementId()).isEqualTo("start-element");
      assertThat(mapped.runtimeInstructions()).hasSize(1);
      assertThat(mapped.runtimeInstructions().getFirst().getAfterElementId())
          .isEqualTo("after-element");
      assertThat(mapped.awaitCompletion()).isFalse();
      assertThat(mapped.fetchVariables()).containsExactly("foo");
      assertThat(mapped.requestTimeout()).isEqualTo(456L);
      assertThat(mapped.tags()).containsExactly("tag1");
      assertThat(mapped.businessId()).isEqualTo("business-1");
    }

    @Test
    void shouldRejectVersionTagWithoutId() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .processDefinitionVersionTag("release-1");

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getDetail())
          .isEqualTo("processDefinitionVersionTag can only be set when using processDefinitionId.");
    }

    @Test
    void shouldRejectVersionTagTogetherWithVersion() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionId("process-id")
              .processDefinitionVersion(2)
              .processDefinitionVersionTag("release-1");

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getDetail())
          .isEqualTo(
              "Only one of [processDefinitionVersion, processDefinitionVersionTag] is allowed.");
    }

    @Test
    void shouldRejectBlankVersionTag() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionId("process-id")
              .processDefinitionVersionTag(" ");

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getDetail())
          .isEqualTo("No processDefinitionVersionTag provided.");
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
                      new ProcessInstanceCreationRuntimeInstruction()
                          .afterElementId("after-element")
                          .type("TERMINATE_PROCESS_INSTANCE")));

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
                      new ProcessInstanceCreationRuntimeInstruction()
                          .afterElementId("after-element")
                          .type("TERMINATE_PROCESS_INSTANCE")));

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
    void shouldKeepDefaultsForSharedFieldsSetToNull() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction().processDefinitionId("process-id");
      request.setVariables(null);
      request.setAwaitCompletion(null);
      request.setFetchVariables(null);
      request.setRequestTimeout(null);
      request.setTags(null);

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isRight()).isTrue();
      final var mapped = result.get();
      assertThat(mapped.variables()).isEmpty();
      assertThat(mapped.awaitCompletion()).isFalse();
      assertThat(mapped.fetchVariables()).isEmpty();
      assertThat(mapped.requestTimeout()).isZero();
      assertThat(mapped.tags()).isEmpty();
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
                      new ProcessInstanceCreationRuntimeInstruction()
                          .afterElementId("after-element")
                          .type("TERMINATE_PROCESS_INSTANCE")));

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

    @Test
    void shouldRejectBusinessIdExceedingMaxLength() {
      // given
      final var request =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey("123")
              .businessId("a".repeat(257));

      // when
      final Either<ProblemDetail, ProcessInstanceCreateRequest> result =
          SimpleRequestMapper.toCreateProcessInstance(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().getStatus()).isEqualTo(400);
      assertThat(result.getLeft().getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(result.getLeft().getDetail()).contains("businessId").contains("256");
    }
  }
}
