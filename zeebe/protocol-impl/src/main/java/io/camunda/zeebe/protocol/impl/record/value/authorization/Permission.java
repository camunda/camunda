/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class Permission extends ObjectValue implements PermissionValue {

  private final EnumProperty<PermissionType> permissionTypeProp =
      new EnumProperty<>("permissionType", PermissionType.class);
  private final ArrayProperty<StringValue> resourceIdsProp =
      new ArrayProperty<>("resourceIds", StringValue::new);

  public Permission() {
    super(2);
    declareProperty(permissionTypeProp).declareProperty(resourceIdsProp);
  }

  public Permission copy(final PermissionValue object) {
    setPermissionType(object.getPermissionType()).addResourceIds(object.getResourceIds());
    return this;
  }

  @Override
  public PermissionType getPermissionType() {
    return permissionTypeProp.getValue();
  }

  public Permission setPermissionType(final PermissionType permissionType) {
    permissionTypeProp.setValue(permissionType);
    return this;
  }

  @Override
  public Set<String> getResourceIds() {
    return StreamSupport.stream(resourceIdsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toSet());
  }

  public Permission addResourceIds(final Set<String> resourceIds) {
    resourceIds.forEach(this::addResourceId);
    return this;
  }

  public Permission addResourceId(final String resourceId) {
    resourceIdsProp.add().wrap(BufferUtil.wrapString(resourceId));
    return this;
  }

  public Permission addResourceId(final long key) {
    return addResourceId(Long.toString(key));
  }
}
