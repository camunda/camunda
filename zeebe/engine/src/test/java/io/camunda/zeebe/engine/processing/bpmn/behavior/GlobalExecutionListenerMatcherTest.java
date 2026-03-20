/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GlobalExecutionListenerMatcherTest {

  @Nested
  class ElementTypeMapping {

    @Test
    void shouldMapProcessToConfigName() {
      assertThat(GlobalExecutionListenerMatcher.getConfigElementTypeName(BpmnElementType.PROCESS))
          .isEqualTo("process");
    }

    @Test
    void shouldMapSubProcessToConfigName() {
      assertThat(
              GlobalExecutionListenerMatcher.getConfigElementTypeName(BpmnElementType.SUB_PROCESS))
          .isEqualTo("subprocess");
    }

    @Test
    void shouldMapEventSubProcessToConfigName() {
      assertThat(
              GlobalExecutionListenerMatcher.getConfigElementTypeName(
                  BpmnElementType.EVENT_SUB_PROCESS))
          .isEqualTo("eventSubprocess");
    }

    @Test
    void shouldMapMultiInstanceBodyToConfigName() {
      assertThat(
              GlobalExecutionListenerMatcher.getConfigElementTypeName(
                  BpmnElementType.MULTI_INSTANCE_BODY))
          .isEqualTo("multiInstanceBody");
    }

    @Test
    void shouldMapServiceTaskToConfigName() {
      assertThat(
              GlobalExecutionListenerMatcher.getConfigElementTypeName(BpmnElementType.SERVICE_TASK))
          .isEqualTo("serviceTask");
    }

    @Test
    void shouldReturnNullForUnsupportedElementType() {
      assertThat(
              GlobalExecutionListenerMatcher.getConfigElementTypeName(
                  BpmnElementType.SEQUENCE_FLOW))
          .isNull();
    }

    @Test
    void shouldResolveConfigNameToElementType() {
      assertThat(GlobalExecutionListenerMatcher.resolveElementType("serviceTask"))
          .isEqualTo(BpmnElementType.SERVICE_TASK);
    }

    @Test
    void shouldResolveSubprocessConfigName() {
      assertThat(GlobalExecutionListenerMatcher.resolveElementType("subprocess"))
          .isEqualTo(BpmnElementType.SUB_PROCESS);
    }

    @Test
    void shouldReturnNullForUnknownConfigName() {
      assertThat(GlobalExecutionListenerMatcher.resolveElementType("unknown")).isNull();
    }

    @Test
    void shouldHaveBidirectionalMapping() {
      // given
      final var configName = "eventSubprocess";

      // when
      final var bpmnType = GlobalExecutionListenerMatcher.resolveElementType(configName);
      final var roundTripped = GlobalExecutionListenerMatcher.getConfigElementTypeName(bpmnType);

      // then
      assertThat(roundTripped).isEqualTo(configName);
    }
  }

  @Nested
  class MatchesElementType {

    @Test
    void shouldMatchWhenBothElementTypesAndCategoriesAreEmpty() {
      // given — empty config means "all"
      final List<String> elementTypes = Collections.emptyList();
      final List<String> categories = Collections.emptyList();

      // when/then — matches any supported element type
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK, elementTypes, categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.PROCESS, elementTypes, categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.EXCLUSIVE_GATEWAY, elementTypes, categories))
          .isTrue();
    }

    @Test
    void shouldMatchWhenBothAreNull() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK, null, null))
          .isTrue();
    }

    @Test
    void shouldMatchExplicitElementType() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK, List.of("serviceTask"), Collections.emptyList()))
          .isTrue();
    }

    @Test
    void shouldNotMatchDifferentElementType() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.USER_TASK, List.of("serviceTask"), Collections.emptyList()))
          .isFalse();
    }

    @Test
    void shouldMatchCategoryTasks() {
      // given
      final List<String> categories = List.of("tasks");

      // when/then
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK, Collections.emptyList(), categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.USER_TASK, Collections.emptyList(), categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SEND_TASK, Collections.emptyList(), categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.RECEIVE_TASK, Collections.emptyList(), categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SCRIPT_TASK, Collections.emptyList(), categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.BUSINESS_RULE_TASK, Collections.emptyList(), categories))
          .isTrue();
    }

    @Test
    void shouldNotMatchNonTaskInTasksCategory() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.PROCESS, Collections.emptyList(), List.of("tasks")))
          .isFalse();
    }

    @Test
    void shouldMatchCategoryGateways() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.EXCLUSIVE_GATEWAY, Collections.emptyList(), List.of("gateways")))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.PARALLEL_GATEWAY, Collections.emptyList(), List.of("gateways")))
          .isTrue();
    }

    @Test
    void shouldMatchCategoryEvents() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.START_EVENT, Collections.emptyList(), List.of("events")))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.BOUNDARY_EVENT, Collections.emptyList(), List.of("events")))
          .isTrue();
    }

    @Test
    void shouldMatchCategoryAll() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK, Collections.emptyList(), List.of("all")))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.PROCESS, Collections.emptyList(), List.of("all")))
          .isTrue();
    }

    @Test
    void shouldMatchUnionOfElementTypesAndCategories() {
      // given — elementTypes has "process", categories has "tasks"
      final var elementTypes = List.of("process");
      final var categories = List.of("tasks");

      // when/then — both process and service task should match
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.PROCESS, elementTypes, categories))
          .isTrue();
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK, elementTypes, categories))
          .isTrue();
      // gateway does not match
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.EXCLUSIVE_GATEWAY, elementTypes, categories))
          .isFalse();
    }

    @Test
    void shouldNotMatchUnsupportedBpmnElementType() {
      // SEQUENCE_FLOW is not mapped
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SEQUENCE_FLOW, Collections.emptyList(), Collections.emptyList()))
          .isFalse();
    }

    @Test
    void shouldNotMatchUnknownCategory() {
      assertThat(
              GlobalExecutionListenerMatcher.matchesElementType(
                  BpmnElementType.SERVICE_TASK,
                  Collections.emptyList(),
                  List.of("unknownCategory")))
          .isFalse();
    }
  }

  @Nested
  class SupportsEventType {

    @Test
    void shouldSupportStartOnProcess() {
      assertThat(GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.PROCESS, "start"))
          .isTrue();
    }

    @Test
    void shouldSupportEndOnProcess() {
      assertThat(GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.PROCESS, "end"))
          .isTrue();
    }

    @Test
    void shouldSupportCancelOnProcess() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.PROCESS, "cancel"))
          .isTrue();
    }

    @Test
    void shouldNotSupportCancelOnServiceTask() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(
                  BpmnElementType.SERVICE_TASK, "cancel"))
          .isFalse();
    }

    @Test
    void shouldSupportStartOnServiceTask() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(
                  BpmnElementType.SERVICE_TASK, "start"))
          .isTrue();
    }

    @Test
    void shouldSupportEndOnServiceTask() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.SERVICE_TASK, "end"))
          .isTrue();
    }

    @Test
    void shouldOnlySupportStartOnGateways() {
      for (final var gateway :
          List.of(
              BpmnElementType.EXCLUSIVE_GATEWAY,
              BpmnElementType.PARALLEL_GATEWAY,
              BpmnElementType.INCLUSIVE_GATEWAY,
              BpmnElementType.EVENT_BASED_GATEWAY)) {
        assertThat(GlobalExecutionListenerMatcher.supportsEventType(gateway, "start"))
            .as("Gateway %s should support start", gateway)
            .isTrue();
        assertThat(GlobalExecutionListenerMatcher.supportsEventType(gateway, "end"))
            .as("Gateway %s should not support end", gateway)
            .isFalse();
        assertThat(GlobalExecutionListenerMatcher.supportsEventType(gateway, "cancel"))
            .as("Gateway %s should not support cancel", gateway)
            .isFalse();
      }
    }

    @Test
    void shouldNotSupportStartOnStartEvent() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(
                  BpmnElementType.START_EVENT, "start"))
          .isFalse();
    }

    @Test
    void shouldSupportEndOnStartEvent() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.START_EVENT, "end"))
          .isTrue();
    }

    @Test
    void shouldSupportStartOnEndEvent() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.END_EVENT, "start"))
          .isTrue();
    }

    @Test
    void shouldNotSupportEndOnEndEvent() {
      assertThat(GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.END_EVENT, "end"))
          .isFalse();
    }

    @Test
    void shouldNotSupportStartOnBoundaryEvent() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(
                  BpmnElementType.BOUNDARY_EVENT, "start"))
          .isFalse();
    }

    @Test
    void shouldSupportEndOnBoundaryEvent() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(
                  BpmnElementType.BOUNDARY_EVENT, "end"))
          .isTrue();
    }

    @Test
    void shouldReturnFalseForUnknownEventType() {
      assertThat(
              GlobalExecutionListenerMatcher.supportsEventType(BpmnElementType.PROCESS, "unknown"))
          .isFalse();
    }

    @ParameterizedTest
    @EnumSource(
        value = BpmnElementType.class,
        names = {
          "PROCESS",
          "SUB_PROCESS",
          "EVENT_SUB_PROCESS",
          "SERVICE_TASK",
          "USER_TASK",
          "SEND_TASK",
          "RECEIVE_TASK",
          "SCRIPT_TASK",
          "BUSINESS_RULE_TASK",
          "CALL_ACTIVITY",
          "MULTI_INSTANCE_BODY",
          "EXCLUSIVE_GATEWAY",
          "PARALLEL_GATEWAY",
          "INCLUSIVE_GATEWAY",
          "EVENT_BASED_GATEWAY",
          "END_EVENT",
          "INTERMEDIATE_CATCH_EVENT",
          "INTERMEDIATE_THROW_EVENT"
        })
    void shouldSupportStartOnAllTasksGatewaysAndProcessTypes(final BpmnElementType elementType) {
      assertThat(GlobalExecutionListenerMatcher.supportsEventType(elementType, "start")).isTrue();
    }
  }
}
