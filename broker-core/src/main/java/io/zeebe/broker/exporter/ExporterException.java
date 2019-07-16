/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

public class ExporterException extends RuntimeException {
  private static final long serialVersionUID = 9144017472787012481L;

  public ExporterException(String message, Throwable cause) {
    super(message, cause);
  }
}
