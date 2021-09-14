/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.jar.ExternalJarClassLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * This suite tests external exporters, that is, those loaded via a self-contained JAR. For thread
 * context class loader (TCL) tests, it will create a JAR which contains a subclass of {@link
 * TclExporter} called {@code com.acme.TestExporter}, which captures the TCL for every method
 * called.
 *
 * <p>To verify that the TCL is correctly set, we compare the captured TCL for each method with the
 * exporter class' class loader. When an external exporter is loaded, a new {@link
 * ExternalJarClassLoader} instance is created for it, and the exporter class has its class loader
 * property set to it. We can then compare the captured TCL with that.
 */
@Execution(ExecutionMode.CONCURRENT)
final class ExternalExporterContainerTest {
  private static final String EXPORTER_CLASS_NAME = "com.acme.TestExporter";
  private ExporterContainerRuntime runtime;

  @BeforeEach
  void beforeEach(final @TempDir Path storagePath) {
    runtime = new ExporterContainerRuntime(storagePath);
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(runtime);
  }

  @Test
  void shouldSetTclOnConfigure(final @TempDir File jarDirectory) throws Exception {
    // given
    final var exporterClass = createUnloadedExporter();
    final var jarFile = exporterClass.toJar(new File(jarDirectory, "exporter.jar"));
    final var descriptor = runtime.loadExternalExporter(jarFile, EXPORTER_CLASS_NAME);
    final var container = runtime.newContainer(descriptor);

    // when
    container.configureExporter();

    // then
    final var exporterInstance = (TclExporter) container.getExporter();
    final var expectedClassLoader = exporterInstance.getClass().getClassLoader();
    final var actualClassLoader = exporterInstance.onConfigureTCL;
    assertThat(actualClassLoader)
        .isSameAs(expectedClassLoader)
        .isInstanceOf(ExternalJarClassLoader.class);
  }

  @Test
  void shouldSetTclOnOpen(final @TempDir File jarDirectory)
      throws ExporterLoadException, IOException {
    // given
    final var exporterClass = createUnloadedExporter();
    final var jarFile = exporterClass.toJar(new File(jarDirectory, "exporter.jar"));
    final var descriptor = runtime.loadExternalExporter(jarFile, EXPORTER_CLASS_NAME);
    final var expectedClassLoader = descriptor.newInstance().getClass().getClassLoader();
    final var container = runtime.newContainer(descriptor);

    // when
    container.openExporter();

    // then
    final var exporterInstance = (TclExporter) container.getExporter();
    assertThat(exporterInstance.onOpenTCL)
        .isSameAs(expectedClassLoader)
        .isInstanceOf(ExternalJarClassLoader.class);
  }

  @Test
  void shouldSetTclOnExport(final @TempDir File jarDirectory)
      throws ExporterLoadException, IOException {
    // given
    final var exporterClass = createUnloadedExporter();
    final var jarFile = exporterClass.toJar(new File(jarDirectory, "exporter.jar"));
    final var descriptor = runtime.loadExternalExporter(jarFile, EXPORTER_CLASS_NAME);
    final var expectedClassLoader = descriptor.newInstance().getClass().getClassLoader();
    final var container = runtime.newContainer(descriptor);

    // when
    final var record = mock(TypedRecord.class);
    // set a high position to ensure we export it
    when(record.getPosition()).thenReturn(Long.MAX_VALUE);
    container.exportRecord(mock(RecordMetadata.class), record);

    // then
    final var exporterInstance = (TclExporter) container.getExporter();
    assertThat(exporterInstance.onExportTCL)
        .isSameAs(expectedClassLoader)
        .isInstanceOf(ExternalJarClassLoader.class);
  }

  @Test
  void shouldSetTclOnClose(final @TempDir File jarDirectory)
      throws ExporterLoadException, IOException {
    // given
    final var exporterClass = createUnloadedExporter();
    final var jarFile = exporterClass.toJar(new File(jarDirectory, "exporter.jar"));
    final var descriptor = runtime.loadExternalExporter(jarFile, EXPORTER_CLASS_NAME);
    final var expectedClassLoader = descriptor.newInstance().getClass().getClassLoader();
    final var container = new ExporterContainer(descriptor);

    // when
    container.close();

    // then
    final var exporterInstance = (TclExporter) container.getExporter();
    assertThat(exporterInstance.onCloseTCL)
        .isSameAs(expectedClassLoader)
        .isInstanceOf(ExternalJarClassLoader.class);
  }

  private Unloaded<TclExporter> createUnloadedExporter() {
    return new ByteBuddy().subclass(TclExporter.class).name(EXPORTER_CLASS_NAME).make();
  }

  // the class must be visible to the generated exporter for ByteBuddy to delegate method invocation
  public abstract static class TclExporter implements Exporter {
    public ClassLoader onConfigureTCL;
    public ClassLoader onOpenTCL;
    public ClassLoader onCloseTCL;
    public ClassLoader onExportTCL;

    @Override
    public void configure(final Context context) throws Exception {
      onConfigureTCL = Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void open(final Controller controller) {
      onOpenTCL = Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void close() {
      onCloseTCL = Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void export(final Record<?> record) {
      onExportTCL = Thread.currentThread().getContextClassLoader();
    }
  }
}
