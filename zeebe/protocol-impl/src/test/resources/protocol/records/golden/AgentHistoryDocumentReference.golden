/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryDocumentReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentHistoryDocumentReference extends ObjectValue
    implements AgentHistoryDocumentReferenceValue {

  private final StringProperty documentIdProp = new StringProperty("documentId", "");
  private final StringProperty storeIdProp = new StringProperty("storeId", "");
  private final StringProperty contentHashProp = new StringProperty("contentHash", "");

  public AgentHistoryDocumentReference() {
    super(3);
    declareProperty(documentIdProp).declareProperty(storeIdProp).declareProperty(contentHashProp);
  }

  @Override
  public String getDocumentId() {
    return BufferUtil.bufferAsString(documentIdProp.getValue());
  }

  public AgentHistoryDocumentReference setDocumentId(final String documentId) {
    documentIdProp.setValue(documentId);
    return this;
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeIdProp.getValue());
  }

  public AgentHistoryDocumentReference setStoreId(final String storeId) {
    storeIdProp.setValue(storeId);
    return this;
  }

  @Override
  public String getContentHash() {
    return BufferUtil.bufferAsString(contentHashProp.getValue());
  }

  public AgentHistoryDocumentReference setContentHash(final String contentHash) {
    contentHashProp.setValue(contentHash);
    return this;
  }

  public void copy(final AgentHistoryDocumentReferenceValue other) {
    setDocumentId(other.getDocumentId());
    setStoreId(other.getStoreId());
    setContentHash(other.getContentHash());
  }
}
