/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;

/**
 * Describes consumer groups (a read; no log write). An empty {@code groupId} means "all groups on
 * the addressed coordinator shard"; a set {@code groupId} narrows it to that one group.
 */
public class DescribeGroupsRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");

  public DescribeGroupsRequest() {
    super(1);
    declareProperty(groupIdProp);
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public DescribeGroupsRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId == null ? "" : groupId);
    return this;
  }
}
