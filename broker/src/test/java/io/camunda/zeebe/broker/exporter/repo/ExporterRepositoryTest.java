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
import io.camunda.zeebe.broker.exporter.util.ExternalExporter;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterRepositoryTest {
  private final ExporterRepository repository = new ExporterRepository();

  @Test
  void shouldCacheDescriptorOnceLoaded() throws ExporterLoadException {
    // given
    final var id = "myExporter";
    final var exporterClass = MinimalExporter.class;

    // when
    final var descriptor = repository.load(id, exporterClass, null);

    // then
    assertThat(descriptor).isNotNull();
    assertThat(repository.load(id, exporterClass)).isSameAs(descriptor);
  }

  @Test
  void shouldFailToLoadIfExporterInvalid() {
    // given
    final var id = "exporter";
    final var exporterClass = InvalidExporter.class;

    // then
    assertThatThrownBy(() -> repository.load(id, exporterClass))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldLoadInternalExporter() throws ExporterLoadException, ExporterJarLoadException {
    // given
    final var config = new ExporterCfg();
    config.setClassName(ControlledTestExporter.class.getCanonicalName());
    config.setJarPath(null);

    // when
    final var descriptor = repository.load("controlled", config);

    // then
    assertThat(config.isExternal()).isFalse();
    assertThat(descriptor.newInstance()).isInstanceOf(ControlledTestExporter.class);
  }

  @Test
  void shouldLoadExternalExporter(final @TempDir File tempDir) throws Exception {
    // given
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));
    final var config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName(ExternalExporter.EXPORTER_CLASS_NAME);
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    args.put("foo", 1);
    args.put("bar", false);

    // when
    final var descriptor = repository.load("exported", config);

    // then
    assertThat(config.isExternal()).isTrue();
    assertThat(descriptor.getConfiguration().getArguments()).isEqualTo(config.getArgs());
    assertThat(descriptor.getConfiguration().getId()).isEqualTo("exported");
    assertThat(descriptor.newInstance().getClass().getCanonicalName())
        .isEqualTo(ExternalExporter.EXPORTER_CLASS_NAME);
  }

  @Test
  void shouldFailToLoadNonExporterClasses(final @TempDir File tempDir) throws IOException {
    // given
    final var externalClass =
        new ByteBuddy().subclass(Object.class).name("com.acme.MyObject").make();
    final var jarFile = externalClass.toJar(new File(tempDir, "library.jar"));
    final var config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();

    // when
    config.setClassName("com.acme.MyObject");
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    // then
    assertThatThrownBy(() -> repository.load("exported", config))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldFailToLoadNonExistingClass(final @TempDir File tempDir) throws IOException {
    // given
    final var exporterClass = ExternalExporter.createUnloadedExporterClass();
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));
    final var config = new ExporterCfg();
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

  static class MinimalExporter implements Exporter {
    @Override
    public void export(final Record<?> record) {}
  }
}
