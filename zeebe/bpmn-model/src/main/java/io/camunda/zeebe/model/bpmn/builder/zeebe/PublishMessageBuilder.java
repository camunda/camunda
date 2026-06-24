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
package io.camunda.zeebe.model.bpmn.builder.zeebe;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractBaseElementBuilder;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePublishMessage;
import java.util.function.Consumer;

public class PublishMessageBuilder
    extends AbstractBaseElementBuilder<PublishMessageBuilder, BaseElement> {
  public final Consumer<Message> consumer;

  public PublishMessageBuilder(
      final BpmnModelInstance modelInstance,
      final BaseElement element,
      final Consumer<Message> consumer) {
    super(modelInstance, element, PublishMessageBuilder.class);
    this.consumer = consumer;
  }

  public PublishMessageBuilder name(final String name) {
    consumer.accept(findMessageForName(name));
    return this;
  }

  public PublishMessageBuilder nameExpression(final String nameExpression) {
    return name(asZeebeExpression(nameExpression));
  }

  /**
   * Sets a static id of the message.
   *
   * @param messageId the id of the message
   * @return the builder object
   */
  public PublishMessageBuilder zeebeMessageId(final String messageId) {
    final ZeebePublishMessage publishMessage =
        getCreateSingleExtensionElement(ZeebePublishMessage.class);
    publishMessage.setMessageId(messageId);

    return myself;
  }

  /**
   * Sets a dynamic id of the message. The id is retrieved from the given expression.
   *
   * @param messageIdExpression the expression for the id of the message
   * @return the builder object
   */
  public PublishMessageBuilder zeebeMessageIdExpression(final String messageIdExpression) {
    return zeebeMessageId(asZeebeExpression(messageIdExpression));
  }

  /**
   * Sets a static correlation key of the message.
   *
   * @param correlationKey the correlation key of the message
   * @return the builder object
   */
  public PublishMessageBuilder zeebeCorrelationKey(String correlationKey) {
    final ZeebePublishMessage publishMessage =
        getCreateSingleExtensionElement(ZeebePublishMessage.class);
    publishMessage.setCorrelationKey(correlationKey);

    return myself;
  }

  /**
   * Sets a dynamic correlation key of the message. The correlation key is retrieved from the given
   * expression.
   *
   * @param correlationKeyExpression the expression for the correlation key of the message
   * @return the builder object
   */
  public PublishMessageBuilder zeebeCorrelationKeyExpression(
      final String correlationKeyExpression) {
    return zeebeCorrelationKey(asZeebeExpression(correlationKeyExpression));
  }

  /**
   * Sets a static time to live of the message.
   *
   * @param timeToLive the correlation key of the message
   * @return the builder object
   */
  public PublishMessageBuilder zeebeTimeToLive(String timeToLive) {
    final ZeebePublishMessage publishMessage =
        getCreateSingleExtensionElement(ZeebePublishMessage.class);
    publishMessage.setTimeToLive(timeToLive);

    return myself;
  }

  /**
   * Sets a dynamic time to live of the message. The time to live is retrieved from the given
   * expression.
   *
   * @param timeToLiveExpression the expression for the time to live of the message
   * @return the builder object
   */
  public PublishMessageBuilder zeebeTimeToLiveExpression(final String timeToLiveExpression) {
    return zeebeTimeToLive(asZeebeExpression(timeToLiveExpression));
  }
}
