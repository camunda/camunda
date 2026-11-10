/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

public class LogBlockRepositoryException extends RuntimeException {

  public LogBlockRepositoryException(final String message) {
    super(message);
  }

  public LogBlockRepositoryException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
