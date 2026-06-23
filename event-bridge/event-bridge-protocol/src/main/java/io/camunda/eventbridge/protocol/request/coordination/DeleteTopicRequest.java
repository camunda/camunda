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

/** Requests removal of a topic from the registry. */
public class DeleteTopicRequest extends UnpackedObject {

  private final StringProperty nameProp = new StringProperty("name", "");

  public DeleteTopicRequest() {
    super(1);
    declareProperty(nameProp);
  }

  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public DeleteTopicRequest setName(final String name) {
    nameProp.setValue(name);
    return this;
  }
}
