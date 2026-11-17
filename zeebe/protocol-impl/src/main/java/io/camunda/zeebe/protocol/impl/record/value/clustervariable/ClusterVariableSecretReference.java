/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class ClusterVariableSecretReference
    extends MsgpackBufferValue<ClusterVariableSecretReference>
    implements ClusterVariableSecretReferenceValue {

  // mirrors io.camunda.secretstore.SecretStoreRegistry#DEFAULT_STORE_ID: the record layer stays
  // free of the secret store API, so the value is repeated here and pinned against the constant
  // by DefaultStoreIdTest. An unset store ID names the default store, not no store at all.
  DirectBuffer storeId = BufferUtil.wrapString("default");
  DirectBuffer secretReference = Decoding.EMPTY;
  // RFC 6901 JSON pointer of the value leaf the reference was found in
  DirectBuffer path = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeId);
  }

  public ClusterVariableSecretReference setStoreId(final String storeId) {
    this.storeId = BufferUtil.wrapString(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReference);
  }

  public ClusterVariableSecretReference setSecretReference(final String secretReference) {
    this.secretReference = BufferUtil.wrapString(secretReference);
    return this;
  }

  @Override
  public String getPath() {
    return BufferUtil.bufferAsString(path);
  }

  public ClusterVariableSecretReference setPath(final String path) {
    this.path = BufferUtil.wrapString(path);
    return this;
  }

  public void copy(final ClusterVariableSecretReferenceValue secretReference) {
    setStoreId(secretReference.getStoreId());
    setSecretReference(secretReference.getSecretReference());
    setPath(secretReference.getPath());
  }

  @Override
  protected MsgpackCodec<ClusterVariableSecretReference> codec() {
    return ClusterVariableSecretReferenceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ClusterVariableSecretReference> ops() {
    return ClusterVariableSecretReferenceOps.INSTANCE;
  }
}
