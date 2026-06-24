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
package io.camunda.zeebe.model.bpmn.impl.instance.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePublishMessage;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebePublishMessageImpl extends BpmnModelElementInstanceImpl
    implements ZeebePublishMessage {

  private static Attribute<String> messageIdAttribute;
  private static Attribute<String> correlationKeyAttribute;
  private static Attribute<String> timeToLiveAttribute;

  public ZeebePublishMessageImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebePublishMessage.class, ZeebeConstants.ELEMENT_PUBLISH_MESSAGE)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebePublishMessageImpl::new);

    correlationKeyAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_CORRELATION_KEY)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    messageIdAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_MESSAGE_ID)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    timeToLiveAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_MESSAGE_TIME_TO_LIVE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }

  @Override
  public String getCorrelationKey() {
    return correlationKeyAttribute.getValue(this);
  }

  @Override
  public void setCorrelationKey(final String correlationKey) {
    correlationKeyAttribute.setValue(this, correlationKey);
  }

  @Override
  public String getMessageId() {
    return messageIdAttribute.getValue(this);
  }

  @Override
  public void setMessageId(final String messageId) {
    messageIdAttribute.setValue(this, messageId);
  }

  @Override
  public String getTimeToLive() {
    return timeToLiveAttribute.getValue(this);
  }

  @Override
  public void setTimeToLive(final String timeToLive) {
    timeToLiveAttribute.setValue(this, timeToLive);
  }
}
