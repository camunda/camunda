/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.value.StringValue;

public final class MsgpackPropertyException extends MsgpackException {

  private static final String MESSAGE_FORMAT = "Property '%s' is invalid: %s";
  private final StringValue property;

  public MsgpackPropertyException(final StringValue property, final String message) {
    this(property, message, null);
  }

  public MsgpackPropertyException(final StringValue property, final Throwable cause) {
    this(property, cause.getMessage(), cause);
  }

  public MsgpackPropertyException(
      final StringValue property, final String message, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, property, message), cause);
    this.property = property;
  }

  public String getProperty() {
    return property.toString();
  }
}
