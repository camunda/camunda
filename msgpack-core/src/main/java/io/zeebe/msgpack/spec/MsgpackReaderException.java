/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.spec;

public class MsgpackReaderException extends MsgpackException {
  private static final long serialVersionUID = 4909839783275678015L;

  public MsgpackReaderException(String message) {
    super(message);
  }

  public MsgpackReaderException(String message, Throwable cause) {
    super(message, cause);
  }

  public MsgpackReaderException(Throwable cause) {
    super(cause);
  }

  public MsgpackReaderException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
