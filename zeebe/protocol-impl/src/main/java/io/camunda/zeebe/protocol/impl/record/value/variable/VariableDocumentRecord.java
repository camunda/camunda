/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class VariableDocumentRecord extends MsgpackBufferValue<VariableDocumentRecord>
    implements MsgpackRecordValue, VariableDocumentRecordValue {

  final RequiredLong scopeKey = new RequiredLong();
  VariableDocumentUpdateSemantic updateSemantics = VariableDocumentUpdateSemantic.PROPAGATE;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public VariableDocumentRecord wrap(final VariableDocumentRecord other) {
    setScopeKey(other.getScopeKey())
        .setVariables(other.getVariablesBuffer())
        .setUpdateSemantics(other.getUpdateSemantics())
        .setStorageOrdinal(other.getStorageOrdinal());

    return this;
  }

  @Override
  public long getScopeKey() {
    return scopeKey.get();
  }

  public VariableDocumentRecord setScopeKey(final long scopeKey) {
    this.scopeKey.set(scopeKey);
    return this;
  }

  @Override
  public VariableDocumentUpdateSemantic getUpdateSemantics() {
    return updateSemantics;
  }

  public VariableDocumentRecord setUpdateSemantics(
      final VariableDocumentUpdateSemantic updateSemantics) {
    this.updateSemantics = updateSemantics;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public VariableDocumentRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public VariableDocumentRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public String getTenantId() {
    // todo(#13388): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }

  @Override
  protected MsgpackCodec<VariableDocumentRecord> codec() {
    return VariableDocumentRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<VariableDocumentRecord> ops() {
    return VariableDocumentRecordOps.INSTANCE;
  }
}
