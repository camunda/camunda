/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CodeQualityTest {

  @Test
  void testAllClassesPrefixEndWithoutDot()
      throws ClassNotFoundException, IllegalAccessException, IOException {
    final List<Class<?>> unifiedConfigurationClasses =
        getAllClassesInPackage("io.camunda.configuration");

    for (final Class<?> clazz : unifiedConfigurationClasses) {
      try {
        final Field prefixField = clazz.getDeclaredField("PREFIX");
        prefixField.setAccessible(true);
        final String prefix = (String) prefixField.get(null);
        assertThat(prefix)
            .withFailMessage("Field " + clazz.getName() + ".PREFIX ends with a dot (.)")
            .doesNotEndWith(".");
      } catch (NoSuchFieldException e) {
        // The class doesn't have the field PREFIX.
        // Nothing to do.
      }
    }
  }

  private static List<Class<?>> getAllClassesInPackage(final String packageName)
      throws ClassNotFoundException, IOException {
    final List<Class<?>> classes = new ArrayList<>();
    final String path = packageName.replace('.', '/');
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    if (classLoader == null) {
      throw new IllegalStateException("Cannot get class loader.");
    }

    final Enumeration<URL> resources = classLoader.getResources(path);
    while (resources.hasMoreElements()) {
      final URL resource = resources.nextElement();
      final File directory = new File(resource.getFile());
      if (directory.exists()) {
        for (final String fileName : directory.list()) {
          if (fileName.endsWith(".class") && !fileName.contains("Test")) {
            final String className =
                packageName + '.' + fileName.substring(0, fileName.length() - 6);
            final Class<?> clazz = Class.forName(className);
            classes.add(clazz);
          }
        }
      }
    }

    return classes;
  }
}
