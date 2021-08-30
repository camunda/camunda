/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor.jar;

import java.nio.file.Path;

public final class InterceptorJarLoadException extends RuntimeException {
  private static final String MESSAGE_FORMAT = "Cannot load JAR at [%s]: %s";
  private static final long serialVersionUID = 1655276726721040696L;

  public InterceptorJarLoadException(final Path jarPath, final String reason) {
    super(String.format(MESSAGE_FORMAT, jarPath, reason));
  }

  public InterceptorJarLoadException(
      final Path jarPath, final String reason, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, jarPath, reason), cause);
  }
}
