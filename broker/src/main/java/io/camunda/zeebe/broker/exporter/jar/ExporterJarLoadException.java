/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.jar;

import java.io.IOException;
import java.nio.file.Path;

public final class ExporterJarLoadException extends IOException {
  private static final String MESSAGE_FORMAT = "Cannot load JAR at [%s]: %s";
  private static final long serialVersionUID = 1655276726721040696L;

  public ExporterJarLoadException(final Path jarPath, final String reason) {
    super(String.format(MESSAGE_FORMAT, jarPath, reason));
  }

  public ExporterJarLoadException(final Path jarPath, final String reason, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, jarPath, reason), cause);
  }
}
