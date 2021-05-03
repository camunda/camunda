/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigurationUtil {
  public static String toAbsolutePath(final String path, final String base) {
    final Path asPath = Paths.get(path);

    if (asPath.isAbsolute()) {
      return path;
    } else {
      return Paths.get(base, path).toString();
    }
  }

  public static void checkPositive(final int value, final String configurationKey) {
    checkArgument(value > 0, "Expected %s to be > 0, but found %s", configurationKey, value);
  }
}
