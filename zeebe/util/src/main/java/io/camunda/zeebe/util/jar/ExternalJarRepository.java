/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.jar;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a map of all loaded external JARs and their corresponding class loaders for quick
 * reuse.
 */
public final class ExternalJarRepository {
  public static final String JAR_EXTENSION = ".jar";

  private final Map<Path, ExternalJarClassLoader> loadedJars;

  public ExternalJarRepository() {
    this(new HashMap<>());
  }

  public ExternalJarRepository(final Map<Path, ExternalJarClassLoader> loadedJars) {
    this.loadedJars = loadedJars;
  }

  public Map<Path, ExternalJarClassLoader> getJars() {
    return Collections.unmodifiableMap(loadedJars);
  }

  public ExternalJarClassLoader remove(final String jarPath) {
    return remove(Paths.get(jarPath));
  }

  public ExternalJarClassLoader remove(final Path jarPath) {
    return loadedJars.remove(jarPath);
  }

  public ExternalJarClassLoader load(final String jarPath) throws ExternalJarLoadException {
    return load(Paths.get(jarPath));
  }

  public ExternalJarClassLoader load(final Path jarPath) throws ExternalJarLoadException {
    ExternalJarClassLoader classLoader = loadedJars.get(jarPath);

    if (classLoader == null) {
      verifyJarPath(jarPath);

      classLoader = ExternalJarClassLoader.ofPath(jarPath);
      loadedJars.put(jarPath, classLoader);
    }

    return classLoader;
  }

  /**
   * Verifies that the given path points to an existing, readable JAR file. Does not perform more
   * complex validation such as checking it is a valid JAR, verifying its signature, etc.
   *
   * @param path path to verify
   * @throws ExternalJarLoadException if it is not a JAR, not readable, or does not exist
   */
  private void verifyJarPath(final Path path) throws ExternalJarLoadException {
    final File jarFile = path.toFile();

    if (!jarFile.getName().endsWith(JAR_EXTENSION)) {
      throw new ExternalJarLoadException(path, "is not a JAR");
    }

    if (!jarFile.canRead()) {
      throw new ExternalJarLoadException(path, "is not readable");
    }
  }
}
