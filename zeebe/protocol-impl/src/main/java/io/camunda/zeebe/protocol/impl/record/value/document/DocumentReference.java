/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class DocumentReference extends MsgpackBufferValue<DocumentReference>
    implements DocumentReferenceValue {

  DirectBuffer documentId = Decoding.EMPTY;
  DirectBuffer storeId = Decoding.EMPTY;
  DirectBuffer contentHash = Decoding.EMPTY;
  final DocumentReferenceMetadata metadata = new DocumentReferenceMetadata();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getDocumentId() {
    return BufferUtil.bufferAsString(documentId);
  }

  public DocumentReference setDocumentId(final String documentId) {
    this.documentId = BufferUtil.wrapString(documentId);
    return this;
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeId);
  }

  public DocumentReference setStoreId(final String storeId) {
    this.storeId = BufferUtil.wrapString(storeId);
    return this;
  }

  @Override
  public String getContentHash() {
    return BufferUtil.bufferAsString(contentHash);
  }

  public DocumentReference setContentHash(final String contentHash) {
    this.contentHash = BufferUtil.wrapString(contentHash);
    return this;
  }

  @Override
  public DocumentReferenceMetadata getMetadata() {
    return metadata;
  }

  public void copy(final DocumentReferenceValue other) {
    setDocumentId(other.getDocumentId());
    setStoreId(other.getStoreId());
    setContentHash(other.getContentHash());
    getMetadata().copy(other.getMetadata());
  }

  @Override
  protected MsgpackCodec<DocumentReference> codec() {
    return DocumentReferenceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DocumentReference> ops() {
    return DocumentReferenceOps.INSTANCE;
  }
}
