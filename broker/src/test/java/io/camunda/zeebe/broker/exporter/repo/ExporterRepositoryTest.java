/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.broker.exporter.util.JarCreatorRule;
import io.camunda.zeebe.broker.exporter.util.TestJarExporter;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class ExporterRepositoryTest {
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final JarCreatorRule jarCreator = new JarCreatorRule(temporaryFolder);

  @Rule public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(jarCreator);

  private final ExporterRepository repository = new ExporterRepository();

  @Test
  public void shouldCacheDescriptorOnceLoaded() throws ExporterLoadException {
    // given
    final String id = "myExporter";
    final Class<? extends Exporter> exporterClass = TestJarExporter.class;

    // when
    final ExporterDescriptor descriptor = repository.load(id, exporterClass, null);

    // then
    assertThat(descriptor).isNotNull();
    assertThat(repository.load(id, exporterClass)).isSameAs(descriptor);
  }

  @Test
  public void shouldFailToLoadIfExporterInvalid() {
    // given
    final String id = "exporter";
    final Class<? extends Exporter> exporterClass = InvalidExporter.class;

    // then
    assertThatThrownBy(() -> repository.load(id, exporterClass))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldLoadInternalExporter() throws ExporterLoadException, ExporterJarLoadException {
    // given
    final ExporterCfg config = new ExporterCfg();
    config.setClassName(ControlledTestExporter.class.getCanonicalName());
    config.setJarPath(null);

    // when
    final ExporterDescriptor descriptor = repository.load("controlled", config);

    // then
    assertThat(config.isExternal()).isFalse();
    assertThat(descriptor.newInstance()).isInstanceOf(ControlledTestExporter.class);
  }

  @Test
  public void shouldLoadExternalExporter() throws Exception {
    // given
    final Class<? extends Exporter> exportedClass = TestJarExporter.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterCfg config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName(exportedClass.getCanonicalName());
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    args.put("foo", 1);
    args.put("bar", false);

    // when
    final ExporterDescriptor descriptor = repository.load("exported", config);

    // then
    assertThat(config.isExternal()).isTrue();
    assertThat(descriptor.getConfiguration().getArguments()).isEqualTo(config.getArgs());
    assertThat(descriptor.getConfiguration().getId()).isEqualTo("exported");
    assertThat(descriptor.newInstance().getClass().getCanonicalName())
        .isEqualTo(exportedClass.getCanonicalName());
  }

  @Test
  public void shouldFailToLoadNonExporterClasses() throws IOException {
    // given
    final Class exportedClass = ExporterRepository.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterCfg config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName(exportedClass.getCanonicalName());
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    // then
    assertThatThrownBy(() -> repository.load("exported", config))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  public void shouldFailToLoadNonExistingClass() throws IOException {
    // given
    final Class exportedClass = ExporterRepository.class;
    final File jarFile = jarCreator.create(exportedClass);
    final ExporterCfg config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName("xyz.i.dont.Exist");
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    // then
    assertThatThrownBy(() -> repository.load("exported", config))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  static class InvalidExporter implements Exporter {
    @Override
    public void configure(final Context context) {
      throw new IllegalStateException("what");
    }

    @Override
    public void export(final Record<?> record) {}
  }
}
