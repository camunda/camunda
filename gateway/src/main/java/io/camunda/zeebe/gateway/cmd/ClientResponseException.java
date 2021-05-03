/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

/**
 * Represents exceptional errors that occur sending a client request and/or handling its response.
 */
public final class ClientResponseException extends ClientException {

  private static final long serialVersionUID = -1143986732133851047L;

  public ClientResponseException(final String message) {
    super(message);
  }

  public ClientResponseException(final Throwable cause) {
    super(cause);
  }

  public ClientResponseException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
