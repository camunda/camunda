/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue.ExpressionSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class ExpressionSecretReference extends MsgpackBufferValue<ExpressionSecretReference>
    implements ExpressionSecretReferenceValue {

  // mirrors io.camunda.secretstore.SecretStoreRegistry#DEFAULT_STORE_ID: the record layer stays
  // free of the secret store API, so the value is repeated here. An unset store ID names the
  // default store, not no store at all.
  DirectBuffer storeId = BufferUtil.wrapString("default");
  DirectBuffer secretReference = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeId);
  }

  public ExpressionSecretReference setStoreId(final String storeId) {
    this.storeId = BufferUtil.wrapString(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReference);
  }

  public ExpressionSecretReference setSecretReference(final String secretReference) {
    this.secretReference = BufferUtil.wrapString(secretReference);
    return this;
  }

  @Override
  protected MsgpackCodec<ExpressionSecretReference> codec() {
    return ExpressionSecretReferenceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ExpressionSecretReference> ops() {
    return ExpressionSecretReferenceOps.INSTANCE;
  }
}
