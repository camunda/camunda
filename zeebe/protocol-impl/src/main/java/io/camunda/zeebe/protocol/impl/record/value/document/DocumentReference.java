/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class DocumentReference extends ObjectValue implements DocumentReferenceValue {

  private final StringProperty documentIdProp = new StringProperty("documentId", "");
  private final StringProperty storeIdProp = new StringProperty("storeId", "");
  private final StringProperty contentHashProp = new StringProperty("contentHash", "");
  private final ObjectProperty<DocumentReferenceMetadata> metadataProp =
      new ObjectProperty<>("metadata", new DocumentReferenceMetadata());

  public DocumentReference() {
    super(4);
    declareProperty(documentIdProp)
        .declareProperty(storeIdProp)
        .declareProperty(contentHashProp)
        .declareProperty(metadataProp);
  }

  @Override
  public String getDocumentId() {
    return BufferUtil.bufferAsString(documentIdProp.getValue());
  }

  public DocumentReference setDocumentId(final String documentId) {
    documentIdProp.setValue(documentId);
    return this;
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeIdProp.getValue());
  }

  public DocumentReference setStoreId(final String storeId) {
    storeIdProp.setValue(storeId);
    return this;
  }

  @Override
  public String getContentHash() {
    return BufferUtil.bufferAsString(contentHashProp.getValue());
  }

  public DocumentReference setContentHash(final String contentHash) {
    contentHashProp.setValue(contentHash);
    return this;
  }

  @Override
  public DocumentReferenceMetadata getMetadata() {
    return metadataProp.getValue();
  }

  public void copy(final DocumentReferenceValue other) {
    setDocumentId(other.getDocumentId());
    setStoreId(other.getStoreId());
    setContentHash(other.getContentHash());
    getMetadata().copy(other.getMetadata());
  }
}
