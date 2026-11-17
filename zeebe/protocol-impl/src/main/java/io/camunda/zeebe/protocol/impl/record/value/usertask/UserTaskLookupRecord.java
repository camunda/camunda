/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.protocol.record.value.UserTaskLookupRecordValue;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class UserTaskLookupRecord extends MsgpackBufferValue<UserTaskLookupRecord>
    implements MsgpackRecordValue, UserTaskLookupRecordValue {

  final List<DirectBuffer> fetchVariables = new ArrayList<>();
  DirectBuffer tenantId = wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  final UserTaskRecord userTask = new UserTaskRecord();
  UserTaskLifecycleState lifecycleState = UserTaskLifecycleState.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public List<String> getFetchVariables() {
    return fetchVariables.stream().map(name -> bufferAsString(name)).toList();
  }

  public UserTaskLookupRecord setFetchVariables(final List<String> fetchVariables) {
    this.fetchVariables.clear();
    fetchVariables.forEach(name -> this.fetchVariables.add(wrapString(name)));
    return this;
  }

  @Override
  public UserTaskRecord getUserTask() {
    return userTask;
  }

  public UserTaskLookupRecord setUserTask(final UserTaskRecord userTask) {
    this.userTask.copyFrom(userTask);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public UserTaskLookupRecord setTenantId(final String tenantId) {
    this.tenantId = wrapString(tenantId);
    return this;
  }

  @Override
  public UserTaskLifecycleState getLifecycleState() {
    return lifecycleState;
  }

  public UserTaskLookupRecord setLifecycleState(final UserTaskLifecycleState lifecycleState) {
    this.lifecycleState = lifecycleState;
    return this;
  }

  @Override
  protected MsgpackCodec<UserTaskLookupRecord> codec() {
    return UserTaskLookupRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<UserTaskLookupRecord> ops() {
    return UserTaskLookupRecordOps.INSTANCE;
  }
}
