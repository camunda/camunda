/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.jar;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a class loader which isolates external exporters from other exporters, while exposing
 * our own code to ensure versions match at runtime.
 */
public final class ExternalJarClassLoader extends URLClassLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExternalJarClassLoader.class);

  private static final String JAVA_PACKAGE_PREFIX = "java.";
  private static final String JAR_URL_FORMAT = "jar:%s!/";

  private ExternalJarClassLoader(final URL[] urls) {
    super(urls);
  }

  static ExternalJarClassLoader ofPath(final Path jarPath) throws ExternalJarLoadException {
    final URL jarUrl;

    try {
      final String expandedPath = jarPath.toUri().toURL().toString();
      jarUrl = new URL(String.format(JAR_URL_FORMAT, expandedPath));
    } catch (final MalformedURLException e) {
      throw new ExternalJarLoadException(jarPath, "bad JAR url", e);
    }

    return new ExternalJarClassLoader(new URL[] {jarUrl});
  }

  @Override
  public Class<?> loadClass(final String name) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      if (name.startsWith(JAVA_PACKAGE_PREFIX)) {
        return findSystemClass(name);
      }

      Class<?> clazz = findLoadedClass(name);
      if (clazz == null) {
        try {
          clazz = findClass(name);
        } catch (final ClassNotFoundException ex) {
          LOGGER.trace("Failed to load class {}, falling back to parent class loader", name, ex);
          clazz = super.loadClass(name);
        }
      }

      return clazz;
    }
  }
}
