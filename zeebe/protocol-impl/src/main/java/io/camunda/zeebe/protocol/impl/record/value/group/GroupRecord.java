/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.group;

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
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class GroupRecord extends MsgpackBufferValue<GroupRecord>
    implements MsgpackRecordValue, GroupRecordValue {

  long groupKey = -1L;
  @Nullable DirectBuffer groupId;
  DirectBuffer name = Decoding.EMPTY;
  DirectBuffer description = Decoding.EMPTY;
  DirectBuffer entityId = Decoding.EMPTY;
  EntityType entityType = EntityType.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getGroupKey() {
    return groupKey;
  }

  public GroupRecord setGroupKey(final long groupKey) {
    this.groupKey = groupKey;
    return this;
  }

  @Override
  public String getGroupId() {
    return BufferUtil.bufferAsString(Decoding.required(groupId, "groupId"));
  }

  public GroupRecord setGroupId(final String groupId) {
    this.groupId = BufferUtil.wrapString(groupId);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(name);
  }

  public GroupRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  @Override
  public String getDescription() {
    return BufferUtil.bufferAsString(description);
  }

  public GroupRecord setDescription(final String description) {
    if (description == null) {
      this.description = Decoding.EMPTY;
      return this;
    }

    this.description = BufferUtil.wrapString(description);
    return this;
  }

  @Override
  public String getEntityId() {
    return BufferUtil.bufferAsString(entityId);
  }

  public GroupRecord setEntityId(final String entityId) {
    this.entityId = BufferUtil.wrapString(entityId);
    return this;
  }

  @Override
  public EntityType getEntityType() {
    return entityType;
  }

  public GroupRecord setEntityType(final EntityType entityType) {
    this.entityType = entityType;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return name;
  }

  @Override
  protected MsgpackCodec<GroupRecord> codec() {
    return GroupRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<GroupRecord> ops() {
    return GroupRecordOps.INSTANCE;
  }
}
