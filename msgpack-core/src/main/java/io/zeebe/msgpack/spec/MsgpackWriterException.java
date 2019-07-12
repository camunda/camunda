/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.spec;

public class MsgpackWriterException extends MsgpackException {
  private static final long serialVersionUID = 6432471139267928246L;

  public MsgpackWriterException(String message) {
    super(message);
  }

  public MsgpackWriterException(String message, Throwable cause) {
    super(message, cause);
  }

  public MsgpackWriterException(Throwable cause) {
    super(cause);
  }

  public MsgpackWriterException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
