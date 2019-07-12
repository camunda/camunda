/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.cmd;

public class ClientOutOfMemoryException extends ClientException {
  private static final String MESSAGE =
      "Broker client is out of buffer memory and cannot make new "
          + "requests until memory is reclaimed.";
  private static final long serialVersionUID = 1L;

  public ClientOutOfMemoryException() {
    super(MESSAGE);
  }

  public ClientOutOfMemoryException(Throwable cause) {
    super(MESSAGE, cause);
  }
}
