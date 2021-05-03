/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.jar;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Provides a class loader which isolates external exporters from other exporters, while exposing
 * our own code to ensure versions match at runtime.
 */
public final class ExporterJarClassLoader extends URLClassLoader {
  private static final String JAVA_PACKAGE_PREFIX = "java.";
  private static final String JAR_URL_FORMAT = "jar:%s!/";

  /** lists of packages from broker base that are exposed at runtime to the external exporters */
  private static final String[] EXPOSED_PACKAGE_PREFIXES =
      new String[] {"io.zeebe.exporter.api", "org.slf4j.", "org.apache.logging.log4j."};

  private ExporterJarClassLoader(final URL[] urls) {
    super(urls);
  }

  static ExporterJarClassLoader ofPath(final Path jarPath) throws ExporterJarLoadException {
    final URL jarUrl;

    try {
      final String expandedPath = jarPath.toUri().toURL().toString();
      jarUrl = new URL(String.format(JAR_URL_FORMAT, expandedPath));
    } catch (final MalformedURLException e) {
      throw new ExporterJarLoadException(jarPath, "bad JAR url", e);
    }

    return new ExporterJarClassLoader(new URL[] {jarUrl});
  }

  @Override
  public Class<?> loadClass(final String name) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      if (name.startsWith(JAVA_PACKAGE_PREFIX)) {
        return findSystemClass(name);
      }

      if (isProvidedByBroker(name)) {
        return getSystemClassLoader().loadClass(name);
      }

      Class<?> clazz = findLoadedClass(name);
      if (clazz == null) {
        try {
          clazz = findClass(name);
        } catch (final ClassNotFoundException ex) {
          clazz = super.loadClass(name);
        }
      }

      return clazz;
    }
  }

  private boolean isProvidedByBroker(final String name) {
    for (final String prefix : EXPOSED_PACKAGE_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }
}
