/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

public class RequestTimeoutException extends TransportException {

  private static final long serialVersionUID = 1L;

  public RequestTimeoutException(String message) {
    super(message);
  }
}
