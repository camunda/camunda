/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentHistoryMessageContent extends ObjectValue
    implements AgentHistoryMessageContentValue {

  private final EnumProperty<AgentHistoryContentType> contentTypeProp =
      new EnumProperty<>(
          "contentType", AgentHistoryContentType.class, AgentHistoryContentType.UNSPECIFIED);
  private final StringProperty textProp = new StringProperty("text", "");
  private final ObjectProperty<AgentHistoryDocumentReference> documentReferenceProp =
      new ObjectProperty<>("documentReference", new AgentHistoryDocumentReference());
  private final DocumentProperty objectProp = new DocumentProperty("object");

  public AgentHistoryMessageContent() {
    super(4);
    declareProperty(contentTypeProp)
        .declareProperty(textProp)
        .declareProperty(documentReferenceProp)
        .declareProperty(objectProp);
  }

  @Override
  public AgentHistoryContentType getContentType() {
    return contentTypeProp.getValue();
  }

  public AgentHistoryMessageContent setContentType(final AgentHistoryContentType contentType) {
    contentTypeProp.setValue(contentType);
    return this;
  }

  @Override
  public String getText() {
    return BufferUtil.bufferAsString(textProp.getValue());
  }

  public AgentHistoryMessageContent setText(final String text) {
    textProp.setValue(text);
    return this;
  }

  @Override
  public AgentHistoryDocumentReference getDocumentReference() {
    return documentReferenceProp.getValue();
  }

  @Override
  public Map<String, Object> getObject() {
    return MsgPackConverter.convertToMap(objectProp.getValue());
  }

  public AgentHistoryMessageContent setObject(final DirectBuffer object) {
    objectProp.setValue(object);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getObjectBuffer() {
    return objectProp.getValue();
  }

  public void copy(final AgentHistoryMessageContentValue other) {
    setContentType(other.getContentType());
    setText(other.getText());
    getDocumentReference().copy(other.getDocumentReference());
    setObject(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(other.getObject())));
  }
}
