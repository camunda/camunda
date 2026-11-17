/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class PolicyDeleteEntry extends MsgpackBufferValue<PolicyDeleteEntry>
    implements PolicyDeleteEntryValue {

  PolicyUpdateEntityType entityType = PolicyUpdateEntityType.UNSPECIFIED;
  DirectBuffer id = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PolicyUpdateEntityType getEntityType() {
    return entityType;
  }

  public PolicyDeleteEntry setEntityType(final PolicyUpdateEntityType entityType) {
    this.entityType = entityType;
    return this;
  }

  @Override
  public String getId() {
    return BufferUtil.bufferAsString(id);
  }

  public PolicyDeleteEntry setId(final String id) {
    this.id = BufferUtil.wrapString(id);
    return this;
  }

  @Override
  protected MsgpackCodec<PolicyDeleteEntry> codec() {
    return PolicyDeleteEntryCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PolicyDeleteEntry> ops() {
    return PolicyDeleteEntryOps.INSTANCE;
  }
}
