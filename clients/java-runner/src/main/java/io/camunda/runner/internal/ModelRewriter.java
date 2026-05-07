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
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Map;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * Clones a {@link BpmnModelInstance} and rewrites it for an isolated run:
 *
 * <ul>
 *   <li>Prefix the {@code processId} (so concurrent devs / runs don't collide on deploy).
 *   <li>Prefix every bound {@code zeebeJobType} (so this run's workers exclusively activate this
 *       run's jobs).
 *   <li>Leave element ids clean so Operate displays readable names.
 * </ul>
 */
public final class ModelRewriter {

  private ModelRewriter() {}

  /** The result of a rewrite. */
  public record Rewritten(
      BpmnModelInstance model, String prefixedProcessId, Map<String, String> jobTypesByElementId) {}

  /**
   * Deep-clones {@code original} via the BPMN XML round-trip, applies the prefix, validates the
   * bindings, and returns the rewritten model and bookkeeping.
   *
   * @throws IllegalArgumentException if a bound element is missing or is not a service task
   * @throws IllegalStateException if the model has no executable process
   */
  public static Rewritten rewrite(
      final BpmnModelInstance original,
      final String prefix,
      final Collection<String> boundElementIds) {
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

    final java.util.LinkedHashMap<String, String> jobTypesByElementId =
        new java.util.LinkedHashMap<>();
    for (final String elementId : boundElementIds) {
      final ModelElementInstance element = clone.getModelElementById(elementId);
      if (element == null) {
        throw new IllegalArgumentException(
            "bound element '" + elementId + "' does not exist in the model");
      }
      if (element instanceof UserTask) {
        throw new UnsupportedOperationException("user-task lambda dispatch is not yet supported");
      }
      if (!(element instanceof ServiceTask serviceTask)) {
        throw new IllegalArgumentException(
            "bound element '"
                + elementId
                + "' is not a service task (got "
                + element.getClass().getSimpleName()
                + "); only service tasks support lambda bindings");
      }

      final ZeebeTaskDefinition taskDefinition =
          serviceTask.getSingleExtensionElement(ZeebeTaskDefinition.class);
      if (taskDefinition == null) {
        throw new IllegalArgumentException(
            "service task '" + elementId + "' has no zeebe:taskDefinition; cannot rewrite jobType");
      }
      final String originalType = taskDefinition.getType();
      final String prefixedType = prefix + "-" + (originalType == null ? elementId : originalType);
      taskDefinition.setType(prefixedType);
      jobTypesByElementId.put(elementId, prefixedType);
    }

    return new Rewritten(clone, prefixedProcessId, jobTypesByElementId);
  }
}
