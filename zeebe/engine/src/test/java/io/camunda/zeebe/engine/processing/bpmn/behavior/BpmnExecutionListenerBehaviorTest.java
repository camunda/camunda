/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BpmnExecutionListenerBehaviorTest {

  @Mock private GlobalListenersState globalListenersState;
  @Mock private ExecutableFlowNode flowNode;
  @Mock private BpmnElementContext context;

  private BpmnExecutionListenerBehavior behavior;

  @BeforeEach
  void setUp() {
    behavior = new BpmnExecutionListenerBehavior(globalListenersState);
  }

  @Nested
  class StartListeners {

    @Test
    void shouldReturnBpmnListenersWhenNoGlobalConfig() {
      // given
      when(globalListenersState.getCurrentConfig()).thenReturn(null);
      final var bpmnListeners = List.of(createBpmnExecutionListener("start", "bpmn-type"));
      when(flowNode.getStartExecutionListeners()).thenReturn(bpmnListeners);
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).isSameAs(bpmnListeners);
    }

    @Test
    void shouldReturnBpmnListenersWhenNoGlobalExecutionListeners() {
      // given
      final var config = new GlobalListenerBatchRecord();
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final var bpmnListeners = List.of(createBpmnExecutionListener("start", "bpmn-type"));
      when(flowNode.getStartExecutionListeners()).thenReturn(bpmnListeners);
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).isSameAs(bpmnListeners);
    }

    @Test
    void shouldMergeGlobalBeforeBpmnListeners() {
      // given
      final var config = createConfigWithExecutionListener("global-type", "start", "serviceTask");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final var bpmnListener = createBpmnExecutionListener("start", "bpmn-type");
      when(flowNode.getStartExecutionListeners()).thenReturn(List.of(bpmnListener));
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(2);
      assertThat(getJobType(result.get(0))).isEqualTo("global-type");
      assertThat(getJobType(result.get(1))).isEqualTo("bpmn-type");
    }

    @Test
    void shouldMergeGlobalAfterBpmnListenersWhenAfterNonGlobal() {
      // given
      final var config =
          createConfigWithExecutionListener("global-type", "start", "serviceTask", true);
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final var bpmnListener = createBpmnExecutionListener("start", "bpmn-type");
      when(flowNode.getStartExecutionListeners()).thenReturn(List.of(bpmnListener));
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(2);
      assertThat(getJobType(result.get(0))).isEqualTo("bpmn-type");
      assertThat(getJobType(result.get(1))).isEqualTo("global-type");
    }

    @Test
    void shouldNotMatchGlobalListenerForDifferentElementType() {
      // given — listener targets userTask, element is serviceTask
      final var config = createConfigWithExecutionListener("global-type", "start", "userTask");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final var bpmnListeners = List.of(createBpmnExecutionListener("start", "bpmn-type"));
      when(flowNode.getStartExecutionListeners()).thenReturn(bpmnListeners);
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then — unchanged, global listener doesn't match
      assertThat(result).isSameAs(bpmnListeners);
    }

    @Test
    void shouldNotMatchGlobalListenerWhenEventTypeNotSupported() {
      // given — gateways don't support "end"
      final var config =
          createConfigWithExecutionListener("global-type", "end", "exclusiveGateway");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.EXCLUSIVE_GATEWAY);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldMatchCategoryAll() {
      // given — empty elementTypes + empty categories = all
      final var config = createConfigWithCategory("global-type", "start", "all");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(1);
      assertThat(getJobType(result.get(0))).isEqualTo("global-type");
    }
  }

  @Nested
  class EndListeners {

    @Test
    void shouldMergeEndListeners() {
      // given
      final var config = createConfigWithExecutionListener("global-type", "end", "serviceTask");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final var bpmnListener = createBpmnExecutionListener("end", "bpmn-type");
      when(flowNode.getEndExecutionListeners()).thenReturn(List.of(bpmnListener));
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getEndExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(2);
      assertThat(getJobType(result.get(0))).isEqualTo("global-type");
      assertThat(getJobType(result.get(1))).isEqualTo("bpmn-type");
    }

    @Test
    void shouldNotMatchStartEventOnGatewaysForEndListeners() {
      // given — gateways support only "start", not "end"
      final var config =
          createConfigWithExecutionListener("global-type", "end", "exclusiveGateway");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getEndExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.EXCLUSIVE_GATEWAY);

      // when
      final var result = behavior.getEndExecutionListeners(flowNode, context);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class MergeOrdering {

    @Test
    void shouldMergeBeforeAndAfterGlobalListeners() {
      // given — one before, one after
      final var config = new GlobalListenerBatchRecord();
      addExecutionListenerToConfig(config, "before-global", "start", "serviceTask", false);
      addExecutionListenerToConfig(config, "after-global", "start", "serviceTask", true);
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final var bpmnListener = createBpmnExecutionListener("start", "bpmn-type");
      when(flowNode.getStartExecutionListeners()).thenReturn(List.of(bpmnListener));
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(3);
      assertThat(getJobType(result.get(0))).isEqualTo("before-global");
      assertThat(getJobType(result.get(1))).isEqualTo("bpmn-type");
      assertThat(getJobType(result.get(2))).isEqualTo("after-global");
    }

    @Test
    void shouldReturnOnlyGlobalListenersWhenNoBpmnListeners() {
      // given
      final var config = new GlobalListenerBatchRecord();
      addExecutionListenerToConfig(config, "before-global", "start", "serviceTask", false);
      addExecutionListenerToConfig(config, "after-global", "start", "serviceTask", true);
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(2);
      assertThat(getJobType(result.get(0))).isEqualTo("before-global");
      assertThat(getJobType(result.get(1))).isEqualTo("after-global");
    }
  }

  @Nested
  class CategoryMatching {

    @Test
    void shouldMatchTasksCategory() {
      // given
      final var config = createConfigWithCategory("global-type", "start", "tasks");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.USER_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(1);
    }

    @Test
    void shouldNotMatchTasksCategoryForGateway() {
      // given
      final var config = createConfigWithCategory("global-type", "start", "tasks");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      final List<ExecutionListener> empty = Collections.emptyList();
      when(flowNode.getStartExecutionListeners()).thenReturn(empty);
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.EXCLUSIVE_GATEWAY);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).isSameAs(empty);
    }

    @Test
    void shouldMatchWhenBothElementTypesAndCategoriesEmpty() {
      // given — empty means "all"
      final var config = createConfigWithEmptyTargets("global-type", "start");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.CALL_ACTIVITY);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then
      assertThat(result).hasSize(1);
    }
  }

  @Nested
  class EventTypeFiltering {

    @Test
    void shouldNotMatchWhenEventTypeNotInListenerConfig() {
      // given — listener only handles "end", we ask for start listeners
      final var config = createConfigWithExecutionListener("global-type", "end", "serviceTask");
      when(globalListenersState.getCurrentConfig()).thenReturn(config);
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

      // when
      final var result = behavior.getStartExecutionListeners(flowNode, context);

      // then — "end" listener should not be returned for start phase
      assertThat(result).isEmpty();
    }

    @Test
    void shouldMatchMultipleEventTypesOnSameListener() {
      // given — listener handles both "start" and "end"
      final var config = new GlobalListenerBatchRecord();
      final var listener = new GlobalListenerRecord();
      listener.setListenerType(GlobalListenerType.EXECUTION);
      listener.setType("global-type");
      listener.setRetries(3);
      listener.setAfterNonGlobal(false);
      listener.setPriority(0);
      listener.setEventTypes(List.of("start", "end"));
      listener.setElementTypes(List.of("serviceTask"));
      listener.setCategories(Collections.emptyList());
      config.addListener(listener);
      when(globalListenersState.getCurrentConfig()).thenReturn(config);

      // Start listeners
      when(flowNode.getStartExecutionListeners()).thenReturn(Collections.emptyList());
      when(context.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);
      final var startResult = behavior.getStartExecutionListeners(flowNode, context);
      assertThat(startResult).hasSize(1);

      // End listeners
      when(flowNode.getEndExecutionListeners()).thenReturn(Collections.emptyList());
      final var endResult = behavior.getEndExecutionListeners(flowNode, context);
      assertThat(endResult).hasSize(1);
    }
  }

  // --- Helper methods ---

  private static ExecutionListener createBpmnExecutionListener(
      final String eventType, final String jobType) {
    final var listener = new ExecutionListener();
    listener.setEventType(ZeebeExecutionListenerEventType.valueOf(eventType));
    final var props = new JobWorkerProperties();
    props.setType(new StaticExpression(jobType));
    props.setRetries(new StaticExpression("3"));
    listener.setJobWorkerProperties(props);
    return listener;
  }

  private static GlobalListenerBatchRecord createConfigWithExecutionListener(
      final String jobType, final String eventType, final String elementType) {
    return createConfigWithExecutionListener(jobType, eventType, elementType, false);
  }

  private static GlobalListenerBatchRecord createConfigWithExecutionListener(
      final String jobType,
      final String eventType,
      final String elementType,
      final boolean afterNonGlobal) {
    final var config = new GlobalListenerBatchRecord();
    addExecutionListenerToConfig(config, jobType, eventType, elementType, afterNonGlobal);
    return config;
  }

  private static void addExecutionListenerToConfig(
      final GlobalListenerBatchRecord config,
      final String jobType,
      final String eventType,
      final String elementType,
      final boolean afterNonGlobal) {
    final var listener = new GlobalListenerRecord();
    listener.setListenerType(GlobalListenerType.EXECUTION);
    listener.setType(jobType);
    listener.setRetries(3);
    listener.setAfterNonGlobal(afterNonGlobal);
    listener.setPriority(0);
    listener.setEventTypes(List.of(eventType));
    listener.setElementTypes(List.of(elementType));
    listener.setCategories(Collections.emptyList());
    config.addListener(listener);
  }

  private static GlobalListenerBatchRecord createConfigWithCategory(
      final String jobType, final String eventType, final String category) {
    final var config = new GlobalListenerBatchRecord();
    final var listener = new GlobalListenerRecord();
    listener.setListenerType(GlobalListenerType.EXECUTION);
    listener.setType(jobType);
    listener.setRetries(3);
    listener.setAfterNonGlobal(false);
    listener.setPriority(0);
    listener.setEventTypes(List.of(eventType));
    listener.setElementTypes(Collections.emptyList());
    listener.setCategories(List.of(category));
    config.addListener(listener);
    return config;
  }

  private static GlobalListenerBatchRecord createConfigWithEmptyTargets(
      final String jobType, final String eventType) {
    final var config = new GlobalListenerBatchRecord();
    final var listener = new GlobalListenerRecord();
    listener.setListenerType(GlobalListenerType.EXECUTION);
    listener.setType(jobType);
    listener.setRetries(3);
    listener.setAfterNonGlobal(false);
    listener.setPriority(0);
    listener.setEventTypes(List.of(eventType));
    listener.setElementTypes(Collections.emptyList());
    listener.setCategories(Collections.emptyList());
    config.addListener(listener);
    return config;
  }

  private static String getJobType(final ExecutionListener listener) {
    return listener.getJobWorkerProperties().getType().getExpression();
  }
}
