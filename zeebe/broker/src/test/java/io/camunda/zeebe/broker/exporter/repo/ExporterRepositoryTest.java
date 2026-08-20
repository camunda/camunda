/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.broker.exporter.util.ExternalExporter;
import io.camunda.zeebe.broker.exporter.util.TestExporterFactory;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
  void shouldCacheDescriptorOnceLoaded() throws ExporterLoadException, ExternalJarLoadException {
    // given
    final var id = "myExporter";
    final var exporterClass = MinimalExporter.class;

    // when
    final var descriptor = repository.validateAndAddExporterDescriptor(id, exporterClass, null);

    // then
    assertThat(descriptor).isNotNull();
    assertThat(repository.load(id, null)).isSameAs(descriptor);
  }

  @Test
  void shouldFailToValidateAndConfigureIfExporterInvalid() {
    // given
    final var id = "exporter";

    // then
    assertThatThrownBy(
            () -> repository.validateAndAddExporterDescriptor(id, InvalidExporter.class, null))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldFailToLoadIfExporterClassNotFound() {
    // given
    final var id = "exporter";
    final var config = new ExporterCfg();
    config.setClassName("NonExistingClass.class");
    config.setJarPath(null);

    // then
    assertThatThrownBy(() -> repository.load(id, config))
        .isInstanceOf(ExporterLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void shouldLoadInternalExporter() throws ExporterLoadException, ExternalJarLoadException {
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
  void shouldLoadInternalExporterUsingExporterFactory()
      throws ExporterLoadException, ExternalJarLoadException {
    // GIVEN we have factories
    final List<ExporterDescriptor> exporterDescriptors =
        List.of(new ExporterDescriptor(TestExporterFactory.EXPORTER_ID, new TestExporterFactory()));
    final ExporterRepository repository = new ExporterRepository(exporterDescriptors);

    // AND our config
    final var config = new ExporterCfg();
    config.setClassName("not used");
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
    assertThat(descriptor.getConfiguration().arguments()).isEqualTo(config.getArgs());
    assertThat(descriptor.getConfiguration().id()).isEqualTo("exported");
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

  @Test
  void shouldSetTclOnValidation(final @TempDir File tempDir) throws IOException {
    // given
    final var config = new ExporterCfg();
    final Map<String, Object> args = new HashMap<>();
    final var classGenerator = new ByteBuddy();
    final var exporterClass =
        classGenerator
            .subclass(TclValidationExporter.class)
            .name(ExternalExporter.EXPORTER_CLASS_NAME)
            .make();
    final var jarFile = exporterClass.toJar(new File(tempDir, "exporter.jar"));
    final var configClass = classGenerator.subclass(Object.class).name("com.acme.Config").make();
    configClass.inject(jarFile);

    // when
    config.setClassName(ExternalExporter.EXPORTER_CLASS_NAME);
    config.setJarPath(jarFile.getAbsolutePath());
    config.setArgs(args);

    // then - if the thread context class loader is incorrectly set, then the configuration class
    // will not be loadable and an exception is thrown during validation
    assertThatCode(() -> repository.load("external", config)).doesNotThrowAnyException();
  }

  /**
   * This implementation will attempt to load the class com.acme.Config via the thread context
   * classloader. It's useful to test that the class loader is correctly set at validation time.
   *
   * <p>NOTE: although we only care about configure, we should implement the interface anyway to
   * catch at compile time if the configure method ever changes.
   *
   * <p>NOTE: this class must also be public in order for the generated external exporter to
   * subclass it
   */
  public static class TclValidationExporter implements Exporter {

    @Override
    public void configure(final Context context) throws Exception {
      Thread.currentThread().getContextClassLoader().loadClass("com.acme.Config");
    }

    @Override
    public void export(final Record<?> record) {}
  }

  public static class InvalidExporter implements Exporter {
    @Override
    public void configure(final Context context) {
      throw new IllegalStateException("what");
    }

    @Override
    public void export(final Record<?> record) {}
  }

  public static class MinimalExporter implements Exporter {
    @Override
    public void export(final Record<?> record) {}
  }
}
