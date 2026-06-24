/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** The groups a coordinator shard knows about, with their observable lifecycle state. */
public class DescribeGroupsResponse extends UnpackedObject {

  private final ArrayProperty<GroupDescriptionValue> groupsProp =
      new ArrayProperty<>("groups", GroupDescriptionValue::new);

  public DescribeGroupsResponse() {
    super(1);
    declareProperty(groupsProp);
  }

  /**
   * Maps each group description to an immutable value <em>during</em> iteration — the {@link
   * ArrayProperty} reuses one flyweight per step, so the mapper must read it before the next
   * element overwrites it.
   */
  public <T> List<T> mapGroups(final Function<GroupDescriptionValue, T> mapper) {
    final var result = new ArrayList<T>();
    groupsProp.forEach(group -> result.add(mapper.apply(group)));
    return result;
  }

  /** Appends a group description, populated by the given builder. */
  public DescribeGroupsResponse addGroup(final Consumer<GroupDescriptionValue> builder) {
    builder.accept(groupsProp.add());
    return this;
  }
}
