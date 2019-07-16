/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.cmd;

public class NoTopologyAvailableException extends ClientException {
  private static final long serialVersionUID = 7035483927294101779L;

  public NoTopologyAvailableException(String message) {
    super(message);
  }

  public NoTopologyAvailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
