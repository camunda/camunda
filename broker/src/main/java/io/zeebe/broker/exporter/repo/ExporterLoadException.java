/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.repo;

public final class ExporterLoadException extends Exception {
  private static final long serialVersionUID = -9192947670450762759L;
  private static final String MESSAGE_FORMAT = "Cannot load exporter [%s]: %s";

  public ExporterLoadException(final String id, final String reason) {
    super(String.format(MESSAGE_FORMAT, id, reason));
  }

  public ExporterLoadException(final String id, final String reason, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, id, reason), cause);
  }
}
