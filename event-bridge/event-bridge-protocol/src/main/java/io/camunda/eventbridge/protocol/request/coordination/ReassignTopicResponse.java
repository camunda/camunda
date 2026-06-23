/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;

public class ReassignTopicResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);

  public ReassignTopicResponse() {
    super(1);
    declareProperty(errorCodeProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public ReassignTopicResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }
}
