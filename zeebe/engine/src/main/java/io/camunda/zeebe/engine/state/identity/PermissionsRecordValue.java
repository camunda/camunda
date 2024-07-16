/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.identity;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PermissionsRecordValue extends UnpackedObject implements DbValue {
  private final ArrayProperty<StringValue> permissionsProp =
      new ArrayProperty<>("permissions", StringValue::new);

  public PermissionsRecordValue() {
    super(1);
    declareProperty(permissionsProp);
  }

  public List<String> getPermissions() {
    return StreamSupport.stream(permissionsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public void setPermissions(final List<String> permissions) {
    permissionsProp.reset();
    permissions.forEach(
        permission -> permissionsProp.add().wrap(BufferUtil.wrapString(permission)));
  }
}
