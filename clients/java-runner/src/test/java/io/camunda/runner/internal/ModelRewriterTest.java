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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ModelRewriterTest {

  private static BpmnModelInstance simpleModel() {
    return Bpmn.createExecutableProcess("order")
        .startEvent()
        .serviceTask("validate", b -> b.zeebeJobType("validate"))
        .endEvent()
        .done();
  }

  @Test
  void shouldClonePreservingOriginalModel() {
    // given
    final BpmnModelInstance original = simpleModel();

    // when
    ModelRewriter.rewrite(original, "stephan-r7f3a", List.of("validate"));

    // then
    final Process originalProcess =
        original.getModelElementsByType(Process.class).iterator().next();
    assertThat(originalProcess.getId()).isEqualTo("order");
    final ServiceTask originalTask = original.getModelElementById("validate");
    assertThat(originalTask.getSingleExtensionElement(ZeebeTaskDefinition.class).getType())
        .isEqualTo("validate");
  }

  @Test
  void shouldPrefixProcessId() {
    // given
    final BpmnModelInstance model = simpleModel();

    // when
    final ModelRewriter.Rewritten result =
        ModelRewriter.rewrite(model, "stephan-r7f3a", List.of("validate"));

    // then
    assertThat(result.prefixedProcessId()).isEqualTo("stephan-r7f3a-order");
    final Process rewritten =
        result.model().getModelElementsByType(Process.class).iterator().next();
    assertThat(rewritten.getId()).isEqualTo("stephan-r7f3a-order");
  }

  @Test
  void shouldPrefixJobTypeForBoundElements() {
    // given
    final BpmnModelInstance model = simpleModel();

    // when
    final ModelRewriter.Rewritten result =
        ModelRewriter.rewrite(model, "stephan-r7f3a", List.of("validate"));

    // then
    final ServiceTask task = result.model().getModelElementById("validate");
    assertThat(task.getSingleExtensionElement(ZeebeTaskDefinition.class).getType())
        .isEqualTo("stephan-r7f3a-validate");
    assertThat(result.jobTypesByElementId()).containsEntry("validate", "stephan-r7f3a-validate");
  }

  @Test
  void shouldLeaveElementIdsClean() {
    // given
    final BpmnModelInstance model = simpleModel();

    // when
    final ModelRewriter.Rewritten result =
        ModelRewriter.rewrite(model, "stephan-r7f3a", List.of("validate"));

    // then
    final ServiceTask task = result.model().getModelElementById("validate");
    assertThat(task).isNotNull();
    assertThat(task.getId()).isEqualTo("validate");
  }

  @Test
  void shouldFailWhenBindingHasNoMatchingElement() {
    // given
    final BpmnModelInstance model = simpleModel();

    // expect
    assertThatThrownBy(() -> ModelRewriter.rewrite(model, "p", Set.of("ghost")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void shouldFailWhenBoundElementIsNotServiceTask() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("p").startEvent().userTask("review").endEvent().done();

    // expect
    assertThatThrownBy(() -> ModelRewriter.rewrite(model, "p", Set.of("review")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("user-task");
  }
}
