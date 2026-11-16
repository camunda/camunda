/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedGroup extends MsgpackBufferValue<PersistedGroup>
    implements DbValue, ProjectionOf<GroupRecord, PersistedGroup> {

  final RequiredLong groupKey = new RequiredLong();
  @Nullable DirectBuffer groupId;
  @Nullable DirectBuffer description;
  @Nullable DirectBuffer name;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedGroup wrap(final GroupRecord group) {
    groupKey.set(group.getGroupKey());
    groupId = BufferUtil.wrapString(group.getGroupId());
    description = BufferUtil.wrapString(group.getDescription());
    name = group.getNameBuffer();
    return this;
  }

  public long getGroupKey() {
    return groupKey.get();
  }

  public PersistedGroup setGroupKey(final long groupKey) {
    this.groupKey.set(groupKey);
    return this;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(Decoding.required(groupId, "groupId"));
  }

  public PersistedGroup setGroupId(final String groupId) {
    this.groupId = BufferUtil.wrapString(groupId);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(Decoding.required(description, "description"));
  }

  public PersistedGroup setDescription(final String description) {
    this.description = BufferUtil.wrapString(description);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(Decoding.required(name, "name"));
  }

  public PersistedGroup setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  @Override
  protected MsgpackCodec<PersistedGroup> codec() {
    return PersistedGroupCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedGroup> ops() {
    return PersistedGroupOps.INSTANCE;
  }
}
