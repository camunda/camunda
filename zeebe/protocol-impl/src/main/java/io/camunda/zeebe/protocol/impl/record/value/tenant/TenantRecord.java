/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.tenant;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class TenantRecord extends MsgpackBufferValue<TenantRecord>
    implements MsgpackRecordValue, TenantRecordValue {
  private static final long DEFAULT_KEY = -1;

  long tenantKey = DEFAULT_KEY;
  @Nullable DirectBuffer tenantId;
  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer description = Decoding.EMPTY;
  DirectBuffer entityId = Decoding.EMPTY;
  EntityType entityType = EntityType.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public TenantRecord copy() {
    final TenantRecord copy = new TenantRecord();
    copy.copyFrom(this);
    return copy;
  }

  @Override
  public long getTenantKey() {
    return tenantKey;
  }

  public TenantRecord setTenantKey(final long tenantKey) {
    this.tenantKey = tenantKey;
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(Decoding.required(tenantId, "tenantId"));
  }

  public TenantRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public TenantRecord setTenantId(final DirectBuffer tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public String getName() {
    return bufferAsString(name);
  }

  public TenantRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  public TenantRecord setName(final DirectBuffer name) {
    this.name = name;
    return this;
  }

  @Override
  public String getDescription() {
    return bufferAsString(description);
  }

  public TenantRecord setDescription(final String description) {
    if (description == null) {
      this.description = Decoding.EMPTY;
      return this;
    }

    this.description = BufferUtil.wrapString(description);
    return this;
  }

  @Override
  public String getEntityId() {
    return bufferAsString(entityId);
  }

  public TenantRecord setEntityId(final String entityId) {
    this.entityId = BufferUtil.wrapString(entityId);
    return this;
  }

  @Override
  public EntityType getEntityType() {
    return entityType;
  }

  public TenantRecord setEntityType(final EntityType entityType) {
    this.entityType = entityType;
    return this;
  }

  public boolean hasTenantKey() {
    return tenantKey != -1L;
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return Decoding.required(tenantId, "tenantId");
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return name;
  }

  @Override
  protected MsgpackCodec<TenantRecord> codec() {
    return TenantRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<TenantRecord> ops() {
    return TenantRecordOps.INSTANCE;
  }
}
