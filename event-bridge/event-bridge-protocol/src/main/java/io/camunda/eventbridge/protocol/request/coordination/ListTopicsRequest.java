/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.UnpackedObject;

/** Requests the current topic registry. Carries no fields. */
public class ListTopicsRequest extends UnpackedObject {
  public ListTopicsRequest() {
    super(0);
  }
}
