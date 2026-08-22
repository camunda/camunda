/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.runner.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListeners;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * Clones a {@link BpmnModelInstance} and rewrites it for an isolated run:
 *
 * <ul>
 *   <li>Prefix the {@code processId} (so concurrent devs / runs don't collide on deploy).
 *   <li>Prefix every bound {@code zeebeJobType} (so this run's workers exclusively activate this
 *       run's jobs).
 *   <li>Prefix the {@code type} attribute on every bound {@code zeebe:executionListener} or {@code
 *       zeebe:taskListener}.
 *   <li>Leave element ids clean so Operate displays readable names.
 * </ul>
 */
public final class ModelRewriter {

  private ModelRewriter() {}

  /** The result of a rewrite. */
  public record Rewritten(
      BpmnModelInstance model,
      String prefixedProcessId,
      Map<BindingKey, String> jobTypesByBinding) {}

  /**
   * Deep-clones {@code original} via the BPMN XML round-trip, applies the prefix, validates the
   * bindings, and returns the rewritten model and bookkeeping.
   *
   * @throws IllegalArgumentException if a bound element / listener is missing
   * @throws IllegalStateException if the model has no executable process
   */
  public static Rewritten rewrite(
      final BpmnModelInstance original,
      final String prefix,
      final Collection<BindingKey> boundKeys) {
    final BpmnModelInstance clone =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(Bpmn.convertToString(original).getBytes(UTF_8)));

    final Process process =
        clone.getModelElementsByType(Process.class).stream()
            .filter(Process::isExecutable)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("model has no executable process"));

    final String originalProcessId = process.getId();
    final String prefixedProcessId = prefix + "-" + originalProcessId;
    process.setId(prefixedProcessId);

    final LinkedHashMap<BindingKey, String> jobTypesByBinding = new LinkedHashMap<>();
    for (final BindingKey key : boundKeys) {
      final String elementId = key.elementId();
      final ModelElementInstance element = clone.getModelElementById(elementId);
      if (element == null) {
        throw new IllegalArgumentException(
            "bound element '" + elementId + "' (binding " + key + ") does not exist in the model");
      }

      final String prefixedType = prefix + "-" + key.placeholderType();

      switch (key.kind()) {
        case SERVICE_TASK -> rewriteServiceTask(element, key, prefixedType);
        case EXECUTION_LISTENER -> rewriteExecutionListener(element, key, prefixedType);
        case TASK_LISTENER -> rewriteTaskListener(element, key, prefixedType);
      }

      jobTypesByBinding.put(key, prefixedType);
    }

    return new Rewritten(clone, prefixedProcessId, jobTypesByBinding);
  }

  private static void rewriteServiceTask(
      final ModelElementInstance element, final BindingKey key, final String prefixedType) {
    if (element instanceof UserTask) {
      throw new UnsupportedOperationException("user-task lambda dispatch is not yet supported");
    }
    if (!(element instanceof ServiceTask serviceTask)) {
      throw new IllegalArgumentException(
          "bound element '"
              + key.elementId()
              + "' is not a service task (got "
              + element.getClass().getSimpleName()
              + "); only service tasks support lambda bindings");
    }
    final ZeebeTaskDefinition taskDefinition =
        serviceTask.getSingleExtensionElement(ZeebeTaskDefinition.class);
    if (taskDefinition == null) {
      throw new IllegalArgumentException(
          "service task '"
              + key.elementId()
              + "' has no zeebe:taskDefinition; cannot rewrite jobType");
    }
    taskDefinition.setType(prefixedType);
  }

  private static void rewriteExecutionListener(
      final ModelElementInstance element, final BindingKey key, final String prefixedType) {
    if (!(element instanceof BaseElement base)) {
      throw new IllegalArgumentException(
          "element '" + key.elementId() + "' does not support execution listeners");
    }
    final ZeebeExecutionListeners listeners =
        base.getSingleExtensionElement(ZeebeExecutionListeners.class);
    final ZeebeExecutionListenerEventType wantEvent =
        "start".equals(key.eventType())
            ? ZeebeExecutionListenerEventType.start
            : ZeebeExecutionListenerEventType.end;
    if (listeners == null) {
      throw new IllegalArgumentException(
          "element '"
              + key.elementId()
              + "' has no <zeebe:executionListener> elements; binding "
              + key
              + " cannot be rewritten");
    }
    ZeebeExecutionListener match = null;
    for (final ZeebeExecutionListener l : listeners.getExecutionListeners()) {
      if (l.getEventType() == wantEvent) {
        match = l;
        break;
      }
    }
    if (match == null) {
      throw new IllegalArgumentException(
          "element '"
              + key.elementId()
              + "' has no <zeebe:executionListener eventType=\""
              + key.eventType()
              + "\"/>; binding "
              + key
              + " cannot be rewritten");
    }
    match.setType(prefixedType);
  }

  private static void rewriteTaskListener(
      final ModelElementInstance element, final BindingKey key, final String prefixedType) {
    if (!(element instanceof UserTask userTask)) {
      throw new IllegalArgumentException(
          "element '"
              + key.elementId()
              + "' is not a UserTask; task-listener bindings require a UserTask");
    }
    final ZeebeTaskListeners listeners =
        userTask.getSingleExtensionElement(ZeebeTaskListeners.class);
    if (listeners == null) {
      throw new IllegalArgumentException(
          "user task '"
              + key.elementId()
              + "' has no <zeebe:taskListener> elements; binding "
              + key
              + " cannot be rewritten");
    }
    final ZeebeTaskListenerEventType wanted = ZeebeTaskListenerEventType.valueOf(key.eventType());
    ZeebeTaskListener match = null;
    for (final ZeebeTaskListener l : listeners.getTaskListeners()) {
      final ZeebeTaskListenerEventType lEvent = l.getEventType();
      if (lEvent != null && lEvent.resolve() == wanted) {
        match = l;
        break;
      }
    }
    if (match == null) {
      throw new IllegalArgumentException(
          "user task '"
              + key.elementId()
              + "' has no <zeebe:taskListener eventType=\""
              + key.eventType()
              + "\"/>; binding "
              + key
              + " cannot be rewritten");
    }
    match.setType(prefixedType);
  }
}
