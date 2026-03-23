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
import io.camunda.gateway.protocol.model.GlobalExecutionListenerCategoryEnum;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerElementTypeEnum;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.UpdateGlobalExecutionListenerRequest;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.IdentifierValidator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

@DisplayName("GlobalListenerRequestValidator Tests")
class GlobalListenerRequestValidatorTest {

  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);
  private static final Pattern GROUP_ID_PATTERN =
      Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  private final GlobalListenerRequestValidator validator =
      new GlobalListenerRequestValidator(new IdentifierValidator(ID_PATTERN, GROUP_ID_PATTERN));

  @Nested
  @DisplayName("Execution listener create validation")
  class ExecutionListenerCreateValidation {

    @Test
    @DisplayName("Should accept valid execution listener create request")
    void shouldAcceptValidRequest() {
      // given
      final var request = new CreateGlobalExecutionListenerRequest();
      request.setId("my-listener");
      request.setType("my-job-type");
      request.setEventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));
      request.setElementTypes(List.of(GlobalExecutionListenerElementTypeEnum.SERVICE_TASK));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject request with empty id")
    void shouldRejectEmptyId() {
      // given
      final var request = new CreateGlobalExecutionListenerRequest();
      request.setId("");
      request.setType("my-job-type");
      request.setEventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("id");
    }

    @Test
    @DisplayName("Should reject request with empty type")
    void shouldRejectEmptyType() {
      // given
      final var request = new CreateGlobalExecutionListenerRequest();
      request.setId("my-listener");
      request.setType("");
      request.setEventTypes(List.of(GlobalExecutionListenerEventTypeEnum.START));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("type");
    }

    @Test
    @DisplayName("Should reject request with empty eventTypes")
    void shouldRejectEmptyEventTypes() {
      // given
      final var request = new CreateGlobalExecutionListenerRequest();
      request.setId("my-listener");
      request.setType("my-job-type");
      request.setEventTypes(List.of());

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("eventTypes");
    }

    @Test
    @DisplayName("Should reject request with null eventTypes")
    void shouldRejectNullEventTypes() {
      // given
      final var request = new CreateGlobalExecutionListenerRequest();
      request.setId("my-listener");
      request.setType("my-job-type");
      request.setEventTypes(null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("eventTypes");
    }
  }

  @Nested
  @DisplayName("Execution listener update validation")
  class ExecutionListenerUpdateValidation {

    @Test
    @DisplayName("Should accept valid execution listener update request")
    void shouldAcceptValidUpdateRequest() {
      // given
      final var request = new UpdateGlobalExecutionListenerRequest();
      request.setType("my-job-type");
      request.setEventTypes(List.of(GlobalExecutionListenerEventTypeEnum.END));
      request.setElementTypes(List.of(GlobalExecutionListenerElementTypeEnum.USER_TASK));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerUpdateRequest("my-listener", request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject update request with invalid id")
    void shouldRejectUpdateWithInvalidId() {
      // given
      final var request = new UpdateGlobalExecutionListenerRequest();
      request.setType("my-job-type");
      request.setEventTypes(List.of(GlobalExecutionListenerEventTypeEnum.END));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerUpdateRequest("$invalid", request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("id");
    }
  }

  @Nested
  @DisplayName("Event-element compatibility validation")
  class EventElementCompatibility {

    @Test
    @DisplayName("Should accept process with start event")
    void shouldAcceptProcessWithStart() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              List.of(GlobalExecutionListenerElementTypeEnum.PROCESS),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should accept process with end event")
    void shouldAcceptProcessWithEnd() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.PROCESS),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should accept process with cancel event")
    void shouldAcceptProcessWithCancel() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.CANCEL),
              List.of(GlobalExecutionListenerElementTypeEnum.PROCESS),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should accept process with all three events")
    void shouldAcceptProcessWithAllEvents() {
      // given
      final var request =
          createRequest(
              List.of(
                  GlobalExecutionListenerEventTypeEnum.START,
                  GlobalExecutionListenerEventTypeEnum.END,
                  GlobalExecutionListenerEventTypeEnum.CANCEL),
              List.of(GlobalExecutionListenerElementTypeEnum.PROCESS),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject cancel event for non-process elements")
    void shouldRejectCancelForServiceTask() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.CANCEL),
              List.of(GlobalExecutionListenerElementTypeEnum.SERVICE_TASK),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("serviceTask").contains("cancel");
    }

    @Test
    @DisplayName("Should reject cancel event for user task")
    void shouldRejectCancelForUserTask() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.CANCEL),
              List.of(GlobalExecutionListenerElementTypeEnum.USER_TASK),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("userTask").contains("cancel");
    }

    @Test
    @DisplayName("Should reject end event for gateways")
    void shouldRejectEndForExclusiveGateway() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.EXCLUSIVE_GATEWAY),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("exclusiveGateway").contains("end");
    }

    @Test
    @DisplayName("Should reject end event for parallel gateway")
    void shouldRejectEndForParallelGateway() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.PARALLEL_GATEWAY),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("parallelGateway").contains("end");
    }

    @Test
    @DisplayName("Should reject end event for inclusive gateway")
    void shouldRejectEndForInclusiveGateway() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.INCLUSIVE_GATEWAY),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("inclusiveGateway").contains("end");
    }

    @Test
    @DisplayName("Should reject end event for event-based gateway")
    void shouldRejectEndForEventBasedGateway() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.EVENT_BASED_GATEWAY),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("eventBasedGateway").contains("end");
    }

    @Test
    @DisplayName("Should accept start-only for gateways")
    void shouldAcceptStartForGateway() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              List.of(GlobalExecutionListenerElementTypeEnum.EXCLUSIVE_GATEWAY),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject start event for startEvent element")
    void shouldRejectStartForStartEvent() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              List.of(GlobalExecutionListenerElementTypeEnum.START_EVENT),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("startEvent").contains("start");
    }

    @Test
    @DisplayName("Should accept end event for startEvent element")
    void shouldAcceptEndForStartEvent() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.START_EVENT),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject end event for endEvent element")
    void shouldRejectEndForEndEvent() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.END_EVENT),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("endEvent").contains("end");
    }

    @Test
    @DisplayName("Should accept start event for endEvent element")
    void shouldAcceptStartForEndEvent() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              List.of(GlobalExecutionListenerElementTypeEnum.END_EVENT),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject start event for boundaryEvent element")
    void shouldRejectStartForBoundaryEvent() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              List.of(GlobalExecutionListenerElementTypeEnum.BOUNDARY_EVENT),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("boundaryEvent").contains("start");
    }

    @Test
    @DisplayName("Should accept end event for boundaryEvent element")
    void shouldAcceptEndForBoundaryEvent() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.BOUNDARY_EVENT),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should accept request with no element types and no categories")
    void shouldAcceptNoElementTypesNoCategoriesNoValidation() {
      // given
      final var request =
          createRequest(List.of(GlobalExecutionListenerEventTypeEnum.START), null, null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should accept service task with start and end events")
    void shouldAcceptServiceTaskWithStartAndEnd() {
      // given
      final var request =
          createRequest(
              List.of(
                  GlobalExecutionListenerEventTypeEnum.START,
                  GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.SERVICE_TASK),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should report multiple violations for multiple incompatible elements")
    void shouldReportMultipleViolations() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(
                  GlobalExecutionListenerElementTypeEnum.EXCLUSIVE_GATEWAY,
                  GlobalExecutionListenerElementTypeEnum.PARALLEL_GATEWAY),
              null);

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("exclusiveGateway").contains("parallelGateway");
    }
  }

  @Nested
  @DisplayName("Category expansion")
  class CategoryExpansion {

    @Test
    @DisplayName("Tasks category should expand to 6 task types and accept start+end")
    void shouldExpandTasksCategory() {
      // given
      final var request =
          createRequest(
              List.of(
                  GlobalExecutionListenerEventTypeEnum.START,
                  GlobalExecutionListenerEventTypeEnum.END),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.TASKS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Tasks category should reject cancel event")
    void shouldRejectCancelForTasksCategory() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.CANCEL),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.TASKS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("cancel");
    }

    @Test
    @DisplayName("Gateways category should accept start event only")
    void shouldAcceptStartForGatewaysCategory() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.GATEWAYS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Gateways category should reject end event")
    void shouldRejectEndForGatewaysCategory() {
      // given
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.GATEWAYS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("end");
    }

    @Test
    @DisplayName("Events category should accept end for startEvent and start for endEvent")
    void shouldRejectStartForEventsCategory() {
      // given — events category includes startEvent(end-only) and boundaryEvent(end-only),
      // so 'start' will fail for those
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.EVENTS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("startEvent").contains("boundaryEvent");
    }

    @Test
    @DisplayName("Events category with end should fail for endEvent")
    void shouldRejectEndForEventsCategoryDueToEndEvent() {
      // given — events category includes endEvent(start-only), so 'end' will fail for it
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.EVENTS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("endEvent");
    }

    @Test
    @DisplayName("All category should expand to all 20 element types")
    void shouldRejectCancelForAllCategory() {
      // given — 'all' category includes all 20 element types;
      // cancel is only valid for process, so it should fail for the other 19
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.CANCEL),
              null,
              List.of(GlobalExecutionListenerCategoryEnum.ALL));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      // Should contain violations for non-process elements
      assertThat(result.get().getDetail()).contains("cancel").contains("serviceTask");
    }

    @Test
    @DisplayName("Combined categories and elementTypes should form a union")
    void shouldUnionCategoriesAndElementTypes() {
      // given — tasks category + process element type; start should be valid for all
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.START),
              List.of(GlobalExecutionListenerElementTypeEnum.PROCESS),
              List.of(GlobalExecutionListenerCategoryEnum.TASKS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Combined categories and elementTypes should validate both groups")
    void shouldValidateBothCategoriesAndElementTypes() {
      // given — gateways category + endEvent element type; end event should fail for both
      final var request =
          createRequest(
              List.of(GlobalExecutionListenerEventTypeEnum.END),
              List.of(GlobalExecutionListenerElementTypeEnum.END_EVENT),
              List.of(GlobalExecutionListenerCategoryEnum.GATEWAYS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerCreateRequest(request);

      // then
      assertThat(result).isPresent();
      // endEvent doesn't support end, gateways don't support end
      assertThat(result.get().getDetail()).contains("endEvent").contains("exclusiveGateway");
    }
  }

  @Nested
  @DisplayName("Update request event-element compatibility")
  class UpdateEventElementCompatibility {

    @Test
    @DisplayName("Update should validate event-element compatibility")
    void shouldValidateUpdateEventElementCompatibility() {
      // given
      final var request = new UpdateGlobalExecutionListenerRequest();
      request.setType("my-job-type");
      request.setEventTypes(List.of(GlobalExecutionListenerEventTypeEnum.CANCEL));
      request.setElementTypes(List.of(GlobalExecutionListenerElementTypeEnum.SERVICE_TASK));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerUpdateRequest("my-listener", request);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getDetail()).contains("serviceTask").contains("cancel");
    }

    @Test
    @DisplayName("Update should accept valid event-element combination")
    void shouldAcceptValidUpdateCombination() {
      // given
      final var request = new UpdateGlobalExecutionListenerRequest();
      request.setType("my-job-type");
      request.setEventTypes(
          List.of(
              GlobalExecutionListenerEventTypeEnum.START,
              GlobalExecutionListenerEventTypeEnum.END));
      request.setCategories(List.of(GlobalExecutionListenerCategoryEnum.TASKS));

      // when
      final Optional<ProblemDetail> result =
          validator.validateExecutionListenerUpdateRequest("my-listener", request);

      // then
      assertThat(result).isEmpty();
    }
  }

  private CreateGlobalExecutionListenerRequest createRequest(
      final List<GlobalExecutionListenerEventTypeEnum> eventTypes,
      final List<GlobalExecutionListenerElementTypeEnum> elementTypes,
      final List<GlobalExecutionListenerCategoryEnum> categories) {
    final var request = new CreateGlobalExecutionListenerRequest();
    request.setId("my-listener");
    request.setType("my-job-type");
    request.setEventTypes(eventTypes);
    request.setElementTypes(elementTypes);
    request.setCategories(categories);
    return request;
  }
}
