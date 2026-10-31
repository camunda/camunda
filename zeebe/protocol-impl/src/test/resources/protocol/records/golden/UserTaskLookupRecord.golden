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

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.protocol.record.value.UserTaskLookupRecordValue;
import java.util.List;
import java.util.stream.StreamSupport;

public final class UserTaskLookupRecord extends UnifiedRecordValue
    implements UserTaskLookupRecordValue {

  private final ArrayProperty<StringValue> fetchVariablesProperty =
      new ArrayProperty<>("fetchVariables", StringValue::new);
  private final StringProperty tenantIdProperty =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final ObjectProperty<UserTaskRecord> userTaskProperty =
      new ObjectProperty<>("userTask", new UserTaskRecord());
  private final EnumProperty<UserTaskLifecycleState> lifecycleStateProperty =
      new EnumProperty<>(
          "lifecycleState", UserTaskLifecycleState.class, UserTaskLifecycleState.UNSPECIFIED);

  public UserTaskLookupRecord() {
    super(4);
    declareProperty(fetchVariablesProperty)
        .declareProperty(tenantIdProperty)
        .declareProperty(userTaskProperty)
        .declareProperty(lifecycleStateProperty);
  }

  @Override
  public List<String> getFetchVariables() {
    return StreamSupport.stream(fetchVariablesProperty.spliterator(), false)
        .map(name -> bufferAsString(name.getValue()))
        .toList();
  }

  public UserTaskLookupRecord setFetchVariables(final List<String> fetchVariables) {
    fetchVariablesProperty.reset();
    fetchVariables.forEach(name -> fetchVariablesProperty.add().wrap(wrapString(name)));
    return this;
  }

  @Override
  public UserTaskRecord getUserTask() {
    return userTaskProperty.getValue();
  }

  public UserTaskLookupRecord setUserTask(final UserTaskRecord userTask) {
    userTaskProperty.getValue().wrap(userTask);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProperty.getValue());
  }

  public UserTaskLookupRecord setTenantId(final String tenantId) {
    tenantIdProperty.setValue(tenantId);
    return this;
  }

  @Override
  public UserTaskLifecycleState getLifecycleState() {
    return lifecycleStateProperty.getValue();
  }

  public UserTaskLookupRecord setLifecycleState(final UserTaskLifecycleState lifecycleState) {
    lifecycleStateProperty.setValue(lifecycleState);
    return this;
  }
}
