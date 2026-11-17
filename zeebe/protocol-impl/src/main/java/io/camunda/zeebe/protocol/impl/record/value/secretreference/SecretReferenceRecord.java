/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.secretreference;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class SecretReferenceRecord extends MsgpackBufferValue<SecretReferenceRecord>
    implements MsgpackRecordValue, SecretReferenceRecordValue {

  // mirrors io.camunda.secretstore.SecretStoreRegistry#DEFAULT_STORE_ID: the record layer stays
  // free of the secret store API, so the value is repeated here and pinned against the constant
  // by DefaultStoreIdTest. An unset store ID names the default store, not no store at all.
  DirectBuffer storeId = BufferUtil.wrapString("default");
  DirectBuffer secretReference = Decoding.EMPTY;
  ResolutionState resolutionState = ResolutionState.UNSPECIFIED;
  final LongArrayList jobKeys = new LongArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeId);
  }

  public SecretReferenceRecord setStoreId(final String storeId) {
    this.storeId = BufferUtil.wrapString(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReference);
  }

  public SecretReferenceRecord setSecretReference(final String secretReference) {
    this.secretReference = BufferUtil.wrapString(secretReference);
    return this;
  }

  @Override
  public ResolutionState getResolutionState() {
    return resolutionState;
  }

  public SecretReferenceRecord setResolutionState(final ResolutionState resolutionState) {
    this.resolutionState = resolutionState;
    return this;
  }

  @Override
  public List<Long> getJobKeys() {
    final var keys = new ArrayList<Long>(jobKeys.size());
    for (int i = 0; i < jobKeys.size(); i++) {
      keys.add(jobKeys.getLong(i));
    }
    return keys;
  }

  public SecretReferenceRecord addJobKey(final long jobKey) {
    jobKeys.addLong(jobKey);
    return this;
  }

  @Override
  protected MsgpackCodec<SecretReferenceRecord> codec() {
    return SecretReferenceRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<SecretReferenceRecord> ops() {
    return SecretReferenceRecordOps.INSTANCE;
  }
}
