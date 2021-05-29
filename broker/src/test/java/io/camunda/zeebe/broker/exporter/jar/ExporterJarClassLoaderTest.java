/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.jar;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.util.JarCreatorRule;
import io.camunda.zeebe.broker.exporter.util.TestJarExporter;
import io.camunda.zeebe.exporter.api.Exporter;
import java.io.File;
import java.lang.reflect.Constructor;
import org.apache.logging.log4j.LogManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

public final class ExporterJarClassLoaderTest {
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final JarCreatorRule jarCreator = new JarCreatorRule(temporaryFolder);

  @Rule public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(jarCreator);

  @Test
  public void shouldLoadClassesPackagedInJar() throws Exception {
    final Class<?> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(exportedClass.getCanonicalName());

    // then
    final Constructor<?> constructor = loadedClass.getConstructor();
    assertThat(loadedClass).isNotEqualTo(exportedClass);
    assertThat(loadedClass.getDeclaredField("FOO").get(loadedClass)).isEqualTo(TestJarExporter.FOO);
    assertThat(constructor.newInstance()).isInstanceOf(Exporter.class);
  }

  @Test
  public void shouldLoadSystemClassesFromSystemClassLoader() throws Exception {
    final Class<?> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(String.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(String.class);
  }

  @Test
  public void shouldLoadZbExporterClassesFromSystemClassLoader() throws Exception {
    final Class<?> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(Exporter.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Exporter.class);
  }

  @Test
  public void shouldLoadSL4JClassesFromSystemClassLoader() throws Exception {
    final Class<?> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(Logger.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(Logger.class);
  }

  @Test
  public void shouldLoadLog4JClassesFromSystemClassLoader() throws Exception {
    final Class<?> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterJarClassLoader classLoader = ExporterJarClassLoader.ofPath(jarFile.toPath());

    // when
    final Class<?> loadedClass = classLoader.loadClass(LogManager.class.getCanonicalName());

    // then
    assertThat(loadedClass).isEqualTo(LogManager.class);
  }
}
