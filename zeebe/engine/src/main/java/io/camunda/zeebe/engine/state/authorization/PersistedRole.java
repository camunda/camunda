/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedRole extends MsgpackBufferValue<PersistedRole> implements DbValue {

  final RequiredLong roleKey = new RequiredLong();
  @Nullable DirectBuffer roleId;
  @Nullable DirectBuffer name;
  @Nullable DirectBuffer description;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getRoleKey() {
    return roleKey.get();
  }

  public PersistedRole setRoleKey(final long roleKey) {
    this.roleKey.set(roleKey);
    return this;
  }

  public String getRoleId() {
    return BufferUtil.bufferAsString(Decoding.required(roleId, "roleId"));
  }

  public String getName() {
    return BufferUtil.bufferAsString(Decoding.required(name, "name"));
  }

  public PersistedRole setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(Decoding.required(description, "description"));
  }

  public PersistedRole setDescription(final String description) {
    this.description = BufferUtil.wrapString(description);
    return this;
  }

  public PersistedRole copy() {
    final var copy = new PersistedRole();
    copy.copyFrom(this);
    return copy;
  }

  /**
   * Wraps the provided RoleRecord into this PersistedRole instance. Copies the properties from the
   * RoleRecord to the corresponding properties of this persisted role, allowing the current
   * instance to reflect the state of the provided record.
   *
   * @param roleRecord the RoleRecord from which to copy the data
   */
  public void from(final RoleRecord roleRecord) {
    roleKey.set(roleRecord.getRoleKey());
    roleId = BufferUtil.wrapString(roleRecord.getRoleId());
    name = BufferUtil.wrapString(roleRecord.getName());
    description = BufferUtil.wrapString(roleRecord.getDescription());
  }

  @Override
  protected MsgpackCodec<PersistedRole> codec() {
    return PersistedRoleCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedRole> ops() {
    return PersistedRoleOps.INSTANCE;
  }
}
