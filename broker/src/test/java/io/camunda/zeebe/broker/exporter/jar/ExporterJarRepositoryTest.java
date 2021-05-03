/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.jar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;

import io.camunda.zeebe.broker.exporter.util.JarCreatorRule;
import io.camunda.zeebe.broker.exporter.util.TestJarExporter;
import java.io.File;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class ExporterJarRepositoryTest {
  private final ExporterJarRepository jarRepository = new ExporterJarRepository();
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final JarCreatorRule jarCreator = new JarCreatorRule(temporaryFolder);
  @Rule public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(jarCreator);

  @Test
  public void shouldThrowExceptionOnLoadIfNotAJar() throws IOException {
    // given
    final File fake = temporaryFolder.newFile("fake-file");

    // then
    assertThatThrownBy(() -> jarRepository.load(fake.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  @Ignore // Temporary disable.. doesn't work on gcloud
  public void shouldThrowExceptionOnLoadIfNotReadable() throws Exception {
    // given
    final File dummy = temporaryFolder.newFile("unreadable.jar");

    // when (ignoring test if file cannot be set to not be readable)
    assumeTrue(dummy.setReadable(false));

    // then
    // System.out.println("was set = " + isSet);
    assertThatThrownBy(() -> jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  public void shouldThrowExceptionIfJarMissing() throws IOException {
    // given
    final File dummy = temporaryFolder.newFile("missing.jar");

    // when
    assertThat(dummy.delete()).isTrue();

    // then
    assertThatThrownBy(() -> jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarLoadException.class);
  }

  @Test
  public void shouldLoadClassLoaderForJar() throws IOException {
    // given
    final File dummy = temporaryFolder.newFile("readable.jar");

    // when (ignoring test if file cannot be set to be readable)
    assumeTrue(dummy.setReadable(true));

    // then
    assertThat(jarRepository.load(dummy.getAbsolutePath()))
        .isInstanceOf(ExporterJarClassLoader.class);
  }

  @Test
  public void shouldLoadClassLoaderCorrectlyOnlyOnce() throws Exception {
    // given
    final Class exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);

    // when
    final ExporterJarClassLoader classLoader = jarRepository.load(jarFile.toPath());

    // then
    assertThat(classLoader.loadClass(exportedClass.getCanonicalName())).isNotEqualTo(exportedClass);
    assertThat(jarRepository.load(jarFile.toPath())).isEqualTo(classLoader);
  }
}
