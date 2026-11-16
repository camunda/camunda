/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceReexportRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ResourceReexportRecord extends MsgpackBufferValue<ResourceReexportRecord>
    implements MsgpackRecordValue, ResourceReexportRecordValue {

  long resourceKey = -1L;
  DirectBuffer tenantId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getResourceKey() {
    return resourceKey;
  }

  public ResourceReexportRecord setResourceKey(final long resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ResourceReexportRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<ResourceReexportRecord> codec() {
    return ResourceReexportRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ResourceReexportRecord> ops() {
    return ResourceReexportRecordOps.INSTANCE;
  }
}
