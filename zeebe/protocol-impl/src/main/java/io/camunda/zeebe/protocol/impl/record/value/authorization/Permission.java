/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class Permission extends MsgpackBufferValue<Permission> implements PermissionValue {

  @Nullable PermissionType permissionType;
  final List<DirectBuffer> resourceIds = new ArrayList<>();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public Permission copy(final PermissionValue object) {
    setPermissionType(object.getPermissionType()).addResourceIds(object.getResourceIds());
    return this;
  }

  @Override
  public PermissionType getPermissionType() {
    return Decoding.required(permissionType, "permissionType");
  }

  public Permission setPermissionType(final PermissionType permissionType) {
    this.permissionType = permissionType;
    return this;
  }

  @Override
  public Set<String> getResourceIds() {
    return resourceIds.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public Permission addResourceIds(final Set<String> resourceIds) {
    resourceIds.forEach(this::addResourceId);
    return this;
  }

  public Permission addResourceId(final String resourceId) {
    resourceIds.add(BufferUtil.wrapString(resourceId));
    return this;
  }

  @Override
  protected MsgpackCodec<Permission> codec() {
    return PermissionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<Permission> ops() {
    return PermissionOps.INSTANCE;
  }
}
