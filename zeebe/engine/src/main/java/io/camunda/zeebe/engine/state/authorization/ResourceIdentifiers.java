/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ResourceIdentifiers extends UnpackedObject implements DbValue {
  private final ArrayProperty<StringValue> resourceIds =
      new ArrayProperty<>("resourceIds", StringValue::new);

  public ResourceIdentifiers() {
    super(1);
    declareProperty(resourceIds);
  }

  public ResourceIdentifiers copy() {
    final ResourceIdentifiers copy = new ResourceIdentifiers();
    copy.setResourceIdentifiers(getResourceIdentifiers());
    return copy;
  }

  public Set<String> getResourceIdentifiers() {
    return StreamSupport.stream(resourceIds.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toSet());
  }

  public void setResourceIdentifiers(final Set<String> permissions) {
    resourceIds.reset();
    permissions.forEach(permission -> resourceIds.add().wrap(BufferUtil.wrapString(permission)));
  }

  public void addResourceIdentifiers(final List<String> resourceIdentifiers) {
    resourceIdentifiers.forEach(
        resourceIdentifier -> resourceIds.add().wrap(BufferUtil.wrapString(resourceIdentifier)));
  }
}
