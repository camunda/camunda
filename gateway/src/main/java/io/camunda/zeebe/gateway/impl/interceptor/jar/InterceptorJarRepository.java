/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor.jar;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a map of all loaded interceptor JARs and their corresponding class loaders for quick
 * reuse.
 */
public final class InterceptorJarRepository {
  public static final String JAR_EXTENSION = ".jar";

  private final Map<Path, InterceptorJarClassLoader> loadedJars = new HashMap<>();

  public InterceptorJarClassLoader load(final String jarPath) throws InterceptorJarLoadException {
    return load(Paths.get(jarPath));
  }

  public InterceptorJarClassLoader load(final Path jarPath) throws InterceptorJarLoadException {
    InterceptorJarClassLoader classLoader = loadedJars.get(jarPath);

    if (classLoader == null) {
      verifyJarPath(jarPath);

      classLoader = InterceptorJarClassLoader.ofPath(jarPath);
      loadedJars.put(jarPath, classLoader);
    }

    return classLoader;
  }

  /**
   * Verifies that the given path points to an existing, readable JAR file. Does not perform more
   * complex validation such as checking it is a valid JAR, verifying its signature, etc.
   *
   * @param path path to verify
   * @throws InterceptorJarLoadException if it is not a JAR, not readable, or does not exist
   */
  private void verifyJarPath(final Path path) throws InterceptorJarLoadException {
    final File jarFile = path.toFile();

    if (!jarFile.getName().endsWith(JAR_EXTENSION)) {
      throw new InterceptorJarLoadException(path, "is not a JAR");
    }

    if (!jarFile.canRead()) {
      throw new InterceptorJarLoadException(path, "is not readable");
    }
  }
}
