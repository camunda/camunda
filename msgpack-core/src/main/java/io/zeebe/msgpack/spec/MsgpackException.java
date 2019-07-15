/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.spec;

public class MsgpackException extends RuntimeException {
  private static final long serialVersionUID = -514144849724509376L;

  public MsgpackException(String message) {
    super(message);
  }

  public MsgpackException(String message, Throwable cause) {
    super(message, cause);
  }

  public MsgpackException(Throwable cause) {
    super(cause);
  }

  public MsgpackException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
