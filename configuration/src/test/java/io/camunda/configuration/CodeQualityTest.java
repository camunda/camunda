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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
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
        final Path root = directory.toPath();
        try (final Stream<Path> files = Files.walk(root)) {
          for (final Path file : files.toList()) {
            final String fileName = file.getFileName().toString();
            if (fileName.endsWith(".class") && !fileName.contains("Test")) {
              final String relative = root.relativize(file).toString();
              final String className =
                  packageName
                      + '.'
                      + relative
                          .substring(0, relative.length() - ".class".length())
                          .replace(File.separatorChar, '.');
              classes.add(Class.forName(className));
            }
          }
        }
      }
    }

    return classes;
  }
}
