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
package io.camunda.zeebe.model.bpmn.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.QueryImpl;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePublishMessage;
import org.junit.jupiter.api.Test;

public class EndEventBuilderTest {

  @Test
  void shouldSetMessageId() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(b -> b.name("message").zeebeMessageId("message-id-1"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);

    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(ZeebePublishMessage::getMessageId)
        .containsExactly("message-id-1");
  }

  @Test
  void shouldSetMessageIdExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(b -> b.name("message").zeebeMessageIdExpression("messageIdExpr"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);

    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(ZeebePublishMessage::getMessageId)
        .containsExactly("=messageIdExpr");
  }

  @Test
  void shouldSetCorrelationKey() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(b -> b.name("message").zeebeCorrelationKey("correlation-key-1"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);
    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(ZeebePublishMessage::getCorrelationKey)
        .containsExactly("correlation-key-1");
  }

  @Test
  void shouldSetCorrelationKeyExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(b -> b.name("message").zeebeCorrelationKeyExpression("correlationKeyExpr"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);
    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(ZeebePublishMessage::getCorrelationKey)
        .containsExactly("=correlationKeyExpr");
  }

  @Test
  void shouldSetTimeToLive() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(b -> b.name("message").zeebeTimeToLive("PT10S"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);
    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(ZeebePublishMessage::getTimeToLive)
        .containsExactly("PT10S");
  }

  @Test
  void shouldSetTimeToLiveExpression() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(b -> b.name("message").zeebeTimeToLiveExpression("timeToLiveExpr"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);
    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(ZeebePublishMessage::getTimeToLive)
        .containsExactly("=timeToLiveExpr");
  }

  @Test
  void shouldSetMessageIdAndCorrelationKeyAndTimeToLive() {
    // when
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("message")
            .message(
                m ->
                    m.zeebeMessageId("message-id")
                        .zeebeCorrelationKey("correlation-key")
                        .zeebeTimeToLive("PT10S"))
            .done();

    // then
    final EndEvent event = instance.getModelElementById("message");
    final MessageEventDefinition messageEventDefinition = getEventDefinition(event);
    final ExtensionElements extensionElements =
        (ExtensionElements)
            messageEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebePublishMessage.class))
        .hasSize(1)
        .extracting(
            ZeebePublishMessage::getMessageId,
            ZeebePublishMessage::getCorrelationKey,
            ZeebePublishMessage::getTimeToLive)
        .containsExactly(tuple("message-id", "correlation-key", "PT10S"));
  }

  private MessageEventDefinition getEventDefinition(final EndEvent event) {
    return new QueryImpl<>(event.getEventDefinitions())
        .filterByType(MessageEventDefinition.class)
        .singleResult();
  }
}
