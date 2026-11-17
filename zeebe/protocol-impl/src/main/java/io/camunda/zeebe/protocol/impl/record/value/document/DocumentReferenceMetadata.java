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
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue.DocumentReferenceMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class DocumentReferenceMetadata extends MsgpackBufferValue<DocumentReferenceMetadata>
    implements DocumentReferenceMetadataValue {

  DirectBuffer contentType = Decoding.EMPTY;
  DirectBuffer fileName = Decoding.EMPTY;
  long expiresAt = -1L;
  long size = -1L;
  DirectBuffer processDefinitionId = Decoding.EMPTY;
  long processInstanceKey = -1L;

  @MsgpackProperty(document = true)
  DirectBuffer customProperties = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getContentType() {
    return BufferUtil.bufferAsString(contentType);
  }

  public DocumentReferenceMetadata setContentType(final String contentType) {
    this.contentType = BufferUtil.wrapString(contentType);
    return this;
  }

  @Override
  public String getFileName() {
    return BufferUtil.bufferAsString(fileName);
  }

  public DocumentReferenceMetadata setFileName(final String fileName) {
    this.fileName = BufferUtil.wrapString(fileName);
    return this;
  }

  @Override
  public long getExpiresAt() {
    return expiresAt;
  }

  public DocumentReferenceMetadata setExpiresAt(final long expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  @Override
  public long getSize() {
    return size;
  }

  public DocumentReferenceMetadata setSize(final long size) {
    this.size = size;
    return this;
  }

  @Override
  public String getProcessDefinitionId() {
    return BufferUtil.bufferAsString(processDefinitionId);
  }

  public DocumentReferenceMetadata setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = BufferUtil.wrapString(processDefinitionId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DocumentReferenceMetadata setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public Map<String, Object> getCustomProperties() {
    return MsgPackConverter.convertToMap(customProperties);
  }

  @JsonIgnore
  public DirectBuffer getCustomPropertiesBuffer() {
    return customProperties;
  }

  public DocumentReferenceMetadata setCustomProperties(final DirectBuffer customProperties) {
    this.customProperties = Decoding.document(customProperties);
    return this;
  }

  public DocumentReferenceMetadata setCustomProperties(final Map<String, Object> customProperties) {
    this.customProperties =
        Decoding.document(
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

  @Override
  protected MsgpackCodec<DocumentReferenceMetadata> codec() {
    return DocumentReferenceMetadataCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DocumentReferenceMetadata> ops() {
    return DocumentReferenceMetadataOps.INSTANCE;
  }
}
