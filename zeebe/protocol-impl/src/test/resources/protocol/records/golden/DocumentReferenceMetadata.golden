/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue.DocumentReferenceMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class DocumentReferenceMetadata extends ObjectValue
    implements DocumentReferenceMetadataValue {

  private final StringProperty contentTypeProp = new StringProperty("contentType", "");
  private final StringProperty fileNameProp = new StringProperty("fileName", "");
  private final LongProperty expiresAtProp = new LongProperty("expiresAt", -1L);
  private final LongProperty sizeProp = new LongProperty("size", -1L);
  private final StringProperty processDefinitionIdProp =
      new StringProperty("processDefinitionId", "");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final DocumentProperty customPropertiesProp = new DocumentProperty("customProperties");

  public DocumentReferenceMetadata() {
    super(7);
    declareProperty(contentTypeProp)
        .declareProperty(fileNameProp)
        .declareProperty(expiresAtProp)
        .declareProperty(sizeProp)
        .declareProperty(processDefinitionIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(customPropertiesProp);
  }

  @Override
  public String getContentType() {
    return BufferUtil.bufferAsString(contentTypeProp.getValue());
  }

  public DocumentReferenceMetadata setContentType(final String contentType) {
    contentTypeProp.setValue(contentType);
    return this;
  }

  @Override
  public String getFileName() {
    return BufferUtil.bufferAsString(fileNameProp.getValue());
  }

  public DocumentReferenceMetadata setFileName(final String fileName) {
    fileNameProp.setValue(fileName);
    return this;
  }

  @Override
  public long getExpiresAt() {
    return expiresAtProp.getValue();
  }

  public DocumentReferenceMetadata setExpiresAt(final long expiresAt) {
    expiresAtProp.setValue(expiresAt);
    return this;
  }

  @Override
  public long getSize() {
    return sizeProp.getValue();
  }

  public DocumentReferenceMetadata setSize(final long size) {
    sizeProp.setValue(size);
    return this;
  }

  @Override
  public String getProcessDefinitionId() {
    return BufferUtil.bufferAsString(processDefinitionIdProp.getValue());
  }

  public DocumentReferenceMetadata setProcessDefinitionId(final String processDefinitionId) {
    processDefinitionIdProp.setValue(processDefinitionId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public DocumentReferenceMetadata setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public Map<String, Object> getCustomProperties() {
    return MsgPackConverter.convertToMap(customPropertiesProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getCustomPropertiesBuffer() {
    return customPropertiesProp.getValue();
  }

  public DocumentReferenceMetadata setCustomProperties(final DirectBuffer customProperties) {
    customPropertiesProp.setValue(customProperties);
    return this;
  }

  public DocumentReferenceMetadata setCustomProperties(final Map<String, Object> customProperties) {
    customPropertiesProp.setValue(
        BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(customProperties)));
    return this;
  }

  public void copy(final DocumentReferenceMetadataValue other) {
    setContentType(other.getContentType());
    setFileName(other.getFileName());
    setExpiresAt(other.getExpiresAt());
    setSize(other.getSize());
    setProcessDefinitionId(other.getProcessDefinitionId());
    setProcessInstanceKey(other.getProcessInstanceKey());
    setCustomProperties(other.getCustomProperties());
  }
}
