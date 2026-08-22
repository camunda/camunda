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
package io.camunda.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.runner.internal.BindingKey;
import io.camunda.runner.internal.BindingKind;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import org.junit.jupiter.api.Test;

/** Builder-side tests for the listener API surface added in Phase 4. */
class LiveBpmnListenerBuilderTest {

  @Test
  void shouldRecordOnStartListenerForServiceTask() {
    // given / when
    final LiveBpmn builder =
        LiveBpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask("validate", (Job j) -> null)
            .on(ZeebeExecutionListenerEventType.start, (Job j) -> {});

    // then
    final BindingKey key = BindingKey.executionListener("validate", "start");
    assertThat(builder.bindings()).containsKey(key);
    assertThat(builder.bindings().get(key)).isNotNull();
    assertThat(key.kind()).isEqualTo(BindingKind.EXECUTION_LISTENER);

    // and the model carries a <zeebe:executionListener eventType="start"/>
    final BpmnModelInstance model = builder.endEvent().done();
    final ServiceTask task = (ServiceTask) model.getModelElementById("validate");
    final ZeebeExecutionListeners listeners =
        task.getSingleExtensionElement(ZeebeExecutionListeners.class);
    assertThat(listeners).isNotNull();
    assertThat(listeners.getExecutionListeners())
        .anyMatch(l -> l.getEventType() == ZeebeExecutionListenerEventType.start);
  }

  @Test
  void shouldRecordOnEndListenerForServiceTask() {
    // given / when
    final LiveBpmn builder =
        LiveBpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask("validate", (Job j) -> null)
            .on(ZeebeExecutionListenerEventType.end, (Job j) -> {});

    // then
    final BindingKey key = BindingKey.executionListener("validate", "end");
    assertThat(builder.bindings()).containsKey(key);

    final BpmnModelInstance model = builder.endEvent().done();
    final ServiceTask task = (ServiceTask) model.getModelElementById("validate");
    final ZeebeExecutionListeners listeners =
        task.getSingleExtensionElement(ZeebeExecutionListeners.class);
    assertThat(listeners.getExecutionListeners())
        .anyMatch(l -> l.getEventType() == ZeebeExecutionListenerEventType.end);
  }

  @Test
  void shouldRecordTaskListenerForUserTask() {
    // given / when
    final LiveBpmn builder =
        LiveBpmn.createExecutableProcess("p")
            .startEvent()
            .userTask("review")
            .on(ZeebeTaskListenerEventType.assigning, (Job j) -> {});

    // then
    final BindingKey key = BindingKey.taskListener("review", ZeebeTaskListenerEventType.assigning);
    assertThat(builder.bindings()).containsKey(key);
    assertThat(key.kind()).isEqualTo(BindingKind.TASK_LISTENER);
    assertThat(key.eventType()).isEqualTo("assigning");
  }

  @Test
  void shouldFailOnTaskListenerForServiceTask() {
    // expect
    assertThatThrownBy(
            () ->
                LiveBpmn.createExecutableProcess("p")
                    .startEvent()
                    .serviceTask("validate", (Job j) -> null)
                    .on(ZeebeTaskListenerEventType.assigning, (Job j) -> {}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("userTask");
  }

  @Test
  void shouldFailOnStartWithoutAnAttachable() {
    // expect — calling .on(start, ...) on a fresh process (only startEvent without id) should fail
    assertThatThrownBy(
            () ->
                LiveBpmn.createExecutableProcess("p")
                    .startEvent()
                    .on(ZeebeExecutionListenerEventType.start, (Job j) -> {}))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRecordViaBindOnStart() {
    // given — adopted model with a pre-existing start execution listener
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask(
                "validate",
                t -> t.zeebeJobType("validate").zeebeStartExecutionListener("original-type"))
            .endEvent()
            .done();

    // when
    final LiveBpmn builder =
        LiveBpmn.of(model).on("validate", ZeebeExecutionListenerEventType.start, (Job j) -> {});

    // then
    assertThat(builder.bindings()).containsKey(BindingKey.executionListener("validate", "start"));
  }

  @Test
  void shouldRecordListenersViaListenersBlock() {
    // given / when — bracketed sub-builder form
    final LiveBpmn builder =
        LiveBpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask("validate", (Job j) -> null)
            .listeners(
                l ->
                    l.on(ZeebeExecutionListenerEventType.start, (Job j) -> {})
                        .on(ZeebeExecutionListenerEventType.end, (Job j) -> {}));

    // then — both bindings recorded under the validate elementId
    assertThat(builder.bindings()).containsKey(BindingKey.executionListener("validate", "start"));
    assertThat(builder.bindings()).containsKey(BindingKey.executionListener("validate", "end"));
  }

  @Test
  void shouldFailBindOnStartWhenListenerMissing() {
    // given — adopted model with NO start listener on validate
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask("validate", t -> t.zeebeJobType("validate"))
            .endEvent()
            .done();

    // expect
    assertThatThrownBy(
            () ->
                LiveBpmn.of(model)
                    .on("validate", ZeebeExecutionListenerEventType.start, (Job j) -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("validate");
  }
}
