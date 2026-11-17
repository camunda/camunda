/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.document.DocumentReference;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
public final class AgentHistoryMessageContent extends MsgpackBufferValue<AgentHistoryMessageContent>
    implements AgentHistoryMessageContentValue {

  private static final DirectBuffer NIL_OBJECT = new UnsafeBuffer(MsgPackHelper.NIL);

  AgentHistoryContentType contentType = AgentHistoryContentType.UNSPECIFIED;
  DirectBuffer text = Decoding.EMPTY;
  final DocumentReference documentReference = new DocumentReference();

  @MsgpackProperty(binary = true)
  DirectBuffer object = NIL_OBJECT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public AgentHistoryContentType getContentType() {
    return contentType;
  }

  public AgentHistoryMessageContent setContentType(final AgentHistoryContentType contentType) {
    this.contentType = contentType;
    return this;
  }

  @Override
  public String getText() {
    return BufferUtil.bufferAsString(text);
  }

  public AgentHistoryMessageContent setText(final String text) {
    this.text = BufferUtil.wrapString(text);
    return this;
  }

  @Override
  public DocumentReference getDocumentReference() {
    return documentReference;
  }

  @Override
  public Object getObject() {
    return MsgPackConverter.convertToObject(object, Object.class);
  }

  public AgentHistoryMessageContent setObject(final Object object) {
    this.object =
        object == null
            ? NIL_OBJECT
            : BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(object));
    return this;
  }

  public void copy(final AgentHistoryMessageContentValue other) {
    setContentType(other.getContentType());
    setText(other.getText());
    getDocumentReference().copy(other.getDocumentReference());
    setObject(other.getObject());
  }

  @Override
  protected MsgpackCodec<AgentHistoryMessageContent> codec() {
    return AgentHistoryMessageContentCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AgentHistoryMessageContent> ops() {
    return AgentHistoryMessageContentOps.INSTANCE;
  }
}
