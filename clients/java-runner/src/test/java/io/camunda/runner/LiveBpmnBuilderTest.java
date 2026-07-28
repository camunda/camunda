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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link LiveBpmn} builder mirrors the {@link Bpmn} builder shape and correctly captures
 * lambda bindings for later use by the runner pipeline.
 *
 * <p>These tests are intentionally written against types that do not yet exist. Compilation failure
 * with "cannot find symbol" for {@code LiveBpmn} and {@code LiveBpmn} is the expected failing state
 * for Phase 1.
 */
class LiveBpmnBuilderTest {

  // -------------------------------------------------------------------------
  // Model equivalence
  // -------------------------------------------------------------------------

  @Test
  void shouldBuildEquivalentBpmnModelForSimpleProcess() {
    // given — explicit ids so element-id comparison is deterministic
    // (the bpmn-model layer assigns random UUIDs when ids are omitted, which would make
    // two independent builds incomparable by id)
    final BpmnModelInstance expected =
        Bpmn.createExecutableProcess("p").startEvent("start").endEvent("end").done();

    // when
    final BpmnModelInstance actual =
        LiveBpmn.createExecutableProcess("p").startEvent("start").endEvent("end").done();

    // then — compare by walking the model, not raw XML string equality
    final Collection<Process> expectedProcesses = expected.getModelElementsByType(Process.class);
    final Collection<Process> actualProcesses = actual.getModelElementsByType(Process.class);

    assertThat(actualProcesses).hasSize(expectedProcesses.size());

    final Process expectedProcess = expectedProcesses.iterator().next();
    final Process actualProcess = actualProcesses.iterator().next();

    assertThat(actualProcess.getId()).isEqualTo(expectedProcess.getId());
    assertThat(actualProcess.isExecutable()).isEqualTo(expectedProcess.isExecutable());

    // compare flow-element ids that we explicitly named (ignoring auto-generated
    // sequence-flow UUIDs which differ between independent builds by design).
    final Set<String> expectedExplicitIds =
        expected.getModelElementsByType(FlowElement.class).stream()
            .map(FlowElement::getId)
            .filter(id -> !id.startsWith("sequenceFlow_"))
            .collect(Collectors.toSet());
    final Set<String> actualExplicitIds =
        actual.getModelElementsByType(FlowElement.class).stream()
            .map(FlowElement::getId)
            .filter(id -> !id.startsWith("sequenceFlow_"))
            .collect(Collectors.toSet());

    assertThat(actualExplicitIds).isEqualTo(expectedExplicitIds);

    // and the count of each FlowElement subtype matches
    final Map<Class<?>, Long> expectedTypeCounts =
        expected.getModelElementsByType(FlowElement.class).stream()
            .collect(Collectors.groupingBy(Object::getClass, Collectors.counting()));
    final Map<Class<?>, Long> actualTypeCounts =
        actual.getModelElementsByType(FlowElement.class).stream()
            .collect(Collectors.groupingBy(Object::getClass, Collectors.counting()));
    assertThat(actualTypeCounts).isEqualTo(expectedTypeCounts);
  }

  // -------------------------------------------------------------------------
  // Pass-through Consumer<ServiceTaskBuilder> overload
  // -------------------------------------------------------------------------

  @Test
  void shouldSupportPassThroughServiceTaskOverload() {
    // given — vanilla Bpmn builder reference
    final BpmnModelInstance reference =
        Bpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask("validate", t -> t.zeebeJobType("validate"))
            .endEvent()
            .done();

    // when — LiveBpmn using the existing Consumer<ServiceTaskBuilder> overload
    final BpmnModelInstance actual =
        LiveBpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask(
                "validate",
                (io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder t) ->
                    t.zeebeJobType("validate"))
            .endEvent()
            .done();

    // then — both models have a service task "validate" with job type "validate"
    final ServiceTask referenceTask = (ServiceTask) reference.getModelElementById("validate");
    final ServiceTask actualTask = (ServiceTask) actual.getModelElementById("validate");

    assertThat(actualTask).isNotNull();

    final ZeebeTaskDefinition referenceTaskDef =
        referenceTask
            .getExtensionElements()
            .getChildElementsByType(ZeebeTaskDefinition.class)
            .iterator()
            .next();
    final ZeebeTaskDefinition actualTaskDef =
        actualTask
            .getExtensionElements()
            .getChildElementsByType(ZeebeTaskDefinition.class)
            .iterator()
            .next();

    assertThat(actualTaskDef.getType()).isEqualTo(referenceTaskDef.getType());
  }

  // -------------------------------------------------------------------------
  // Lambda binding recording — Function<Job, Map> overload for serviceTask
  // -------------------------------------------------------------------------

  @Test
  void shouldRecordLambdaForFunctionOverload() {
    // given
    final LiveBpmn builder = LiveBpmn.createExecutableProcess("p").startEvent();

    // when — Function<Job, Map<String, Object>> overload
    final BpmnModelInstance model =
        builder.serviceTask("validate", (Job job) -> Map.of("valid", true)).endEvent().done();

    // then — binding must be recorded for "validate"
    // Implementer note: expose bindings via a package-private Map<String, Object> bindings()
    // accessor on LiveBpmn (or the return type of createExecutableProcess()).
    // The Map key is the elementId string; value is a non-null handler (any Object).
    assertThat(builder.bindings()).containsKey(BindingKey.serviceTask("validate"));
    assertThat(builder.bindings().get(BindingKey.serviceTask("validate"))).isNotNull();

    // The underlying BPMN element must have *some* zeebeJobType set as a placeholder.
    // Placeholder convention: the elementId itself (i.e. "validate") or the sentinel
    // "__livebpmn__:validate". The implementer MUST document which sentinel they choose.
    // At run() time the type is prefixed; here we only assert it is non-null and non-empty.
    final ServiceTask serviceTask = (ServiceTask) model.getModelElementById("validate");
    assertThat(serviceTask).isNotNull();

    final ZeebeTaskDefinition taskDef =
        serviceTask
            .getExtensionElements()
            .getChildElementsByType(ZeebeTaskDefinition.class)
            .iterator()
            .next();
    assertThat(taskDef.getType()).isNotBlank();
  }

  // -------------------------------------------------------------------------
  // Lambda binding recording — Consumer<Job> overload for serviceTask
  // -------------------------------------------------------------------------

  @Test
  void shouldRecordLambdaForConsumerOverload() {
    // given
    final LiveBpmn builder = LiveBpmn.createExecutableProcess("p").startEvent();

    // when — Consumer<Job> overload (no return value, auto-complete)
    @SuppressWarnings("unchecked")
    final BpmnModelInstance model =
        builder
            .serviceTask(
                "ship",
                (Job job) -> {
                  /* no-op consumer */
                })
            .endEvent()
            .done();

    // then
    assertThat(builder.bindings()).containsKey(BindingKey.serviceTask("ship"));
    assertThat(builder.bindings().get(BindingKey.serviceTask("ship"))).isNotNull();

    final ServiceTask serviceTask = (ServiceTask) model.getModelElementById("ship");
    assertThat(serviceTask).isNotNull();

    final ZeebeTaskDefinition taskDef =
        serviceTask
            .getExtensionElements()
            .getChildElementsByType(ZeebeTaskDefinition.class)
            .iterator()
            .next();
    assertThat(taskDef.getType()).isNotBlank();
  }

  // -------------------------------------------------------------------------
  // Lambda binding recording — Function<Job, Map> overload for userTask
  // -------------------------------------------------------------------------

  @Test
  void shouldRecordLambdaForUserTaskFunctionOverload() {
    // given
    final LiveBpmn builder = LiveBpmn.createExecutableProcess("p").startEvent();

    // when
    final BpmnModelInstance model =
        builder.userTask("review", (Job job) -> Map.of("approved", false)).endEvent().done();

    // then
    assertThat(builder.bindings()).containsKey(BindingKey.serviceTask("review"));
    assertThat(builder.bindings().get(BindingKey.serviceTask("review"))).isNotNull();

    final UserTask userTask = (UserTask) model.getModelElementById("review");
    assertThat(userTask).isNotNull();
  }

  // -------------------------------------------------------------------------
  // Lambda binding recording — Consumer<Job> overload for userTask
  // -------------------------------------------------------------------------

  @Test
  void shouldRecordLambdaForUserTaskConsumerOverload() {
    // given
    final LiveBpmn builder = LiveBpmn.createExecutableProcess("p").startEvent();

    // when
    final BpmnModelInstance model =
        builder
            .userTask(
                "approve",
                (Job job) -> {
                  /* no-op consumer */
                })
            .endEvent()
            .done();

    // then
    assertThat(builder.bindings()).containsKey(BindingKey.serviceTask("approve"));
    assertThat(builder.bindings().get(BindingKey.serviceTask("approve"))).isNotNull();

    final UserTask userTask = (UserTask) model.getModelElementById("approve");
    assertThat(userTask).isNotNull();
  }

  // -------------------------------------------------------------------------
  // .raw() escape hatch
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("rawtypes")
  void shouldExposeRawBuilderForUnmirroredMethods() {
    // given
    final LiveBpmn builder = LiveBpmn.createExecutableProcess("p").startEvent("start");

    // when — call .raw() to access un-mirrored methods (e.g. boundaryEvent)
    final AbstractFlowNodeBuilder<?, ?> rawBuilder = builder.raw();

    // then — raw builder is non-null and is the underlying bpmn-model builder type
    assertThat(rawBuilder).isNotNull();
    assertThat(rawBuilder).isInstanceOf(AbstractFlowNodeBuilder.class);
  }

  // -------------------------------------------------------------------------
  // .done() returns BpmnModelInstance
  // -------------------------------------------------------------------------

  @Test
  void shouldReturnUnderlyingBpmnModelInstanceFromDone() {
    // given / when
    final Object result = LiveBpmn.createExecutableProcess("p").startEvent().endEvent().done();

    // then
    assertThat(result).isInstanceOf(BpmnModelInstance.class);
  }

  // -------------------------------------------------------------------------
  // .of(model).bind() path — records bindings without mutating the original model
  // -------------------------------------------------------------------------

  @Test
  void shouldBindLambdasToExistingModelViaOf() {
    // given — an existing model built with vanilla Bpmn
    final BpmnModelInstance existingModel =
        Bpmn.createExecutableProcess("p")
            .startEvent()
            .serviceTask("validate", t -> t.zeebeJobType("validate"))
            .endEvent()
            .done();

    // when — adopt the model and bind a lambda to an existing service task
    final LiveBpmn builder =
        LiveBpmn.of(existingModel).bind("validate", (Job job) -> Map.of("valid", true));

    // then — binding recorded
    assertThat(builder.bindings()).containsKey(BindingKey.serviceTask("validate"));
    assertThat(builder.bindings().get(BindingKey.serviceTask("validate"))).isNotNull();

    // the original model must not have been structurally altered
    final ServiceTask originalTask = (ServiceTask) existingModel.getModelElementById("validate");
    assertThat(originalTask).isNotNull();
  }

  // -------------------------------------------------------------------------
  // .of(model).bind() fails fast when elementId does not exist in the model
  // -------------------------------------------------------------------------

  @Test
  void shouldFailFastWhenBindingNonExistentElement() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("p").startEvent().endEvent().done();

    // when / then — bind() on a missing elementId must throw at bind() time
    // (model is already in hand so we can validate immediately)
    assertThatThrownBy(() -> LiveBpmn.of(model).bind("nope", (Job job) -> Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nope");
  }
}
