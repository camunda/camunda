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

public class JoinGroupRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty instanceIdProp = new StringProperty("instanceId", "");

  public JoinGroupRequest() {
    super(2);
    declareProperty(groupIdProp).declareProperty(instanceIdProp);
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public JoinGroupRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getInstanceId() {
    return bufferAsString(instanceIdProp.getValue());
  }

  public JoinGroupRequest setInstanceId(final String instanceId) {
    instanceIdProp.reset();
    if (instanceId != null && !instanceId.isEmpty()) {
      instanceIdProp.setValue(instanceId);
    }
    return this;
  }
}
