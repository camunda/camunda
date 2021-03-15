/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

public final class MsgpackWriterException extends MsgpackException {
  private static final long serialVersionUID = 6432471139267928246L;

  public MsgpackWriterException(final String message) {
    super(message);
  }

  public MsgpackWriterException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public MsgpackWriterException(final Throwable cause) {
    super(cause);
  }

  public MsgpackWriterException(
      final String message,
      final Throwable cause,
      final boolean enableSuppression,
      final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
