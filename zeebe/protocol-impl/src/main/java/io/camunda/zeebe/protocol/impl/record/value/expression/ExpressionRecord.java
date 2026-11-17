/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue.ExpressionSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class ExpressionRecord extends MsgpackBufferValue<ExpressionRecord>
    implements MsgpackRecordValue, ExpressionRecordValue {

  @Nullable DirectBuffer expression;

  @MsgpackProperty(binary = true)
  DirectBuffer resultValue = new UnsafeBuffer(new byte[] {0});

  final List<DirectBuffer> warnings = new ArrayList<>();
  DirectBuffer tenantId = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  long scopeKey = -1L;
  final List<ExpressionSecretReference> referencedSecrets = new ArrayList<>();
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getExpression() {
    return BufferUtil.bufferAsString(Decoding.required(expression, "expression"));
  }

  public ExpressionRecord setExpression(final String expression) {
    this.expression = BufferUtil.wrapString(expression);
    return this;
  }

  @Override
  public Object getResultValue() {
    return MsgPackConverter.convertToObject(resultValue, Object.class);
  }

  @Override
  public List<String> getWarnings() {
    return warnings.stream().map(BufferUtil::bufferAsString).collect(Collectors.toList());
  }

  public ExpressionRecord setWarnings(final @Nullable List<String> warnings) {
    this.warnings.clear();
    if (warnings != null) {
      warnings.forEach(warning -> this.warnings.add(BufferUtil.wrapString(warning)));
    }
    return this;
  }

  public ExpressionRecord setResultValue(final DirectBuffer value) {
    resultValue = value;
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public ExpressionRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ExpressionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  public ExpressionRecord setScopeKey(final long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  @Override
  public List<ExpressionSecretReferenceValue> getReferencedSecrets() {
    // copy each element so the returned list stays valid after the record is reset or another
    // element is added
    return referencedSecrets.stream()
        .map(
            reference ->
                (ExpressionSecretReferenceValue)
                    new ExpressionSecretReference()
                        .setStoreId(reference.getStoreId())
                        .setSecretReference(reference.getSecretReference()))
        .collect(Collectors.toList());
  }

  public ExpressionRecord addReferencedSecret(final String storeId, final String secretReference) {
    referencedSecrets.add(
        new ExpressionSecretReference().setStoreId(storeId).setSecretReference(secretReference));
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ExpressionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ExpressionRecord> codec() {
    return ExpressionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ExpressionRecord> ops() {
    return ExpressionRecordOps.INSTANCE;
  }
}
