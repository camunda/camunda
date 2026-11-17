/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedTenant extends MsgpackBufferValue<PersistedTenant> implements DbValue {

  final RequiredLong tenantKey = new RequiredLong();
  @Nullable DirectBuffer tenantId;
  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer description = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PersistedTenant copy() {
    final var copy = new PersistedTenant();
    copy.copyFrom(this);
    return copy;
  }

  /**
   * Gets the tenant key.
   *
   * @return the tenant key as a long value
   */
  public long getTenantKey() {
    return tenantKey.get();
  }

  /**
   * Sets the tenant key.
   *
   * @param tenantKey the tenant key to set
   * @return the current PersistedTenant instance
   */
  public PersistedTenant setTenantKey(final long tenantKey) {
    this.tenantKey.set(tenantKey);
    return this;
  }

  /**
   * Gets the tenant ID.
   *
   * @return the tenant ID as a string
   */
  public String getTenantId() {
    return BufferUtil.bufferAsString(Decoding.required(tenantId, "tenantId"));
  }

  /**
   * Sets the tenant ID.
   *
   * @param tenantId the tenant ID to set
   * @return the current PersistedTenant instance
   */
  public PersistedTenant setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  /**
   * Gets the tenant name.
   *
   * @return the name of the tenant as a string
   */
  public String getName() {
    return BufferUtil.bufferAsString(name);
  }

  /**
   * Sets the tenant name.
   *
   * @param name the tenant name to set
   * @return the current PersistedTenant instance
   */
  public PersistedTenant setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(description);
  }

  public PersistedTenant setDescription(final String description) {
    this.description = BufferUtil.wrapString(description);
    return this;
  }

  /**
   * Wraps the provided TenantRecord into this PersistedTenant instance. Copies the tenant key,
   * tenant ID, and tenant name from the TenantRecord to the corresponding properties of this
   * persisted tenant, allowing the current instance to reflect the state of the provided record.
   *
   * @param tenantRecord the TenantRecord from which to copy the data
   */
  public void from(final TenantRecord tenantRecord) {
    tenantKey.set(tenantRecord.getTenantKey());
    tenantId = BufferUtil.wrapString(tenantRecord.getTenantId());
    name = BufferUtil.wrapString(tenantRecord.getName());
    description = BufferUtil.wrapString(tenantRecord.getDescription());
  }

  @Override
  protected MsgpackCodec<PersistedTenant> codec() {
    return PersistedTenantCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedTenant> ops() {
    return PersistedTenantOps.INSTANCE;
  }
}
