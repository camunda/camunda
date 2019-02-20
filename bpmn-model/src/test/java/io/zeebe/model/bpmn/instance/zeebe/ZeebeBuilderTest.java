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
package io.zeebe.model.bpmn.instance.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.ServiceTask;
import java.util.Collection;
import java.util.function.Predicate;
import org.junit.Test;

public class ZeebeBuilderTest {

  @Test
  public void shouldBuildServiceTask() {
    // when
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask(
                "foo",
                b ->
                    b.zeebeTaskType("taskType")
                        .zeebeTaskRetries(5)
                        .zeebeTaskHeader("foo", "f")
                        .zeebeTaskHeader("bar", "b"))
            .endEvent()
            .done();

    // then
    Bpmn.validateModel(modelInstance);

    final ServiceTask serviceTask = modelInstance.getModelElementById("foo");

    final ZeebeTaskDefinition taskDefinition =
        getExtensionElement(serviceTask, ZeebeTaskDefinition.class);
    assertThat(taskDefinition.getType()).isEqualTo("taskType");
    assertThat(taskDefinition.getRetries()).isEqualTo(5);

    final ZeebeTaskHeaders taskHeaders = getExtensionElement(serviceTask, ZeebeTaskHeaders.class);
    final Collection<ZeebeHeader> headerCollection = taskHeaders.getHeaders();
    assertThat(headerCollection).hasSize(2);
    assertThat(headerCollection).element(0).matches(header("foo", "f"));
    assertThat(headerCollection).element(1).matches(header("bar", "b"));
  }

  @Test
  public void shouldBuildTaskWithIoMapping() {
    // when
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask(
                "foo",
                b ->
                    b.zeebeInput("inputSource", "inputTarget")
                        .zeebeOutput("outputSource", "outputTarget"))
            .endEvent()
            .done();

    // then
    Bpmn.validateModel(modelInstance);

    final ServiceTask serviceTask = modelInstance.getModelElementById("foo");

    final ZeebeIoMapping ioMapping = getExtensionElement(serviceTask, ZeebeIoMapping.class);

    final Collection<ZeebeInput> inputs = ioMapping.getInputs();
    assertThat(inputs).hasSize(1);
    assertThat(inputs).element(0).matches(input("inputSource", "inputTarget"));

    final Collection<ZeebeOutput> outputs = ioMapping.getOutputs();
    assertThat(outputs).hasSize(1);
    assertThat(outputs).element(0).matches(output("outputSource", "outputTarget"));
  }

  @Test
  public void shouldBuildIntermediateMessageCatchEvent() {
    // when
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess()
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(b -> b.name("messageName").zeebeCorrelationKey("correlationKey"))
            .endEvent()
            .done();

    // then
    Bpmn.validateModel(modelInstance);

    final IntermediateCatchEvent catchEvent = modelInstance.getModelElementById("catch");
    final Collection<EventDefinition> definitions = catchEvent.getEventDefinitions();
    assertThat(definitions).hasSize(1);

    final EventDefinition eventDefinition = definitions.iterator().next();
    assertThat(eventDefinition).isInstanceOf(MessageEventDefinition.class);

    final MessageEventDefinition messageEventDefinition = (MessageEventDefinition) eventDefinition;
    final Message message = messageEventDefinition.getMessage();

    assertThat(message).isNotNull();
    assertThat(message.getName()).isEqualTo("messageName");

    final ZeebeSubscription subscription = getExtensionElement(message, ZeebeSubscription.class);
    assertThat(subscription.getCorrelationKey()).isEqualTo("correlationKey");
  }

  @Test
  public void shouldBuildReceiveTask() {
    // when
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess()
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("messageName").zeebeCorrelationKey("correlationKey"))
            .endEvent()
            .done();

    // then
    Bpmn.validateModel(modelInstance);

    final ReceiveTask task = modelInstance.getModelElementById("catch");
    final Message message = task.getMessage();

    assertThat(message).isNotNull();
    assertThat(message.getName()).isEqualTo("messageName");

    final ZeebeSubscription subscription = getExtensionElement(message, ZeebeSubscription.class);
    assertThat(subscription.getCorrelationKey()).isEqualTo("correlationKey");
  }

  @SuppressWarnings("unchecked")
  private <T extends BpmnModelElementInstance> T getExtensionElement(
      BaseElement element, Class<T> typeClass) {
    final T extensionElement =
        (T) element.getExtensionElements().getUniqueChildElementByType(typeClass);
    assertThat(element).isNotNull();
    return extensionElement;
  }

  private static Predicate<ZeebeHeader> header(String key, String value) {
    return h -> key.equals(h.getKey()) && value.equals(h.getValue());
  }

  private static Predicate<ZeebeInput> input(String source, String target) {
    return h -> source.equals(h.getSource()) && target.equals(h.getTarget());
  }

  private static Predicate<ZeebeOutput> output(String source, String target) {
    return h -> source.equals(h.getSource()) && target.equals(h.getTarget());
  }
}
