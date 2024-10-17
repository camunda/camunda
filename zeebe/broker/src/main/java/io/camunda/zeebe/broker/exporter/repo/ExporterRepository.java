/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class ExporterRepository {
  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private static final int NULL_PARTITION_ID = Integer.MIN_VALUE;
  private final ExternalJarRepository jarRepository;
  private final Map<String, ExporterDescriptor> exporters;

  public ExporterRepository() {
    this(new ArrayList<>());
  }

  public ExporterRepository(final List<ExporterDescriptor> exporters) {
    this(exporters, new ExternalJarRepository());
  }

  public ExporterRepository(
      final List<ExporterDescriptor> exporters, final ExternalJarRepository jarRepository) {
    this.exporters =
        exporters.stream()
            .collect(Collectors.toMap(ExporterDescriptor::getId, Function.identity()));
    this.jarRepository = jarRepository;
  }

  public Map<String, ExporterDescriptor> getExporters() {
    return Collections.unmodifiableMap(exporters);
  }

  @VisibleForTesting
  public ExporterDescriptor validateAndAddExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args)
      throws ExporterLoadException {
    ExporterDescriptor descriptor = exporters.get(id);

    if (descriptor == null) {
      descriptor = new ExporterDescriptor(id, exporterClass, args);
      validate(descriptor);

      exporters.put(id, descriptor);
    }

    return descriptor;
  }

  public ExporterDescriptor load(final String id, final ExporterCfg config)
      throws ExporterLoadException, ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends Exporter> exporterClass;

    if (exporters.containsKey(id)) {
      return exporters.get(id);
    }

    if (!config.isExternal()) {
      classLoader = getClass().getClassLoader();
    } else {
      classLoader = jarRepository.load(config.getJarPath());
    }

    try {
      final Class<?> specifiedClass = classLoader.loadClass(config.getClassName());
      exporterClass = specifiedClass.asSubclass(Exporter.class);
    } catch (final ClassNotFoundException | ClassCastException e) {
      throw new ExporterLoadException(id, "cannot load specified class", e);
    }

    return validateAndAddExporterDescriptor(id, exporterClass, config.getArgs());
  }

  private void validate(final ExporterDescriptor descriptor) throws ExporterLoadException {
    try {
      final Exporter instance = descriptor.newInstance();
      try (final var context =
          new ExporterContext(
              LOG,
              descriptor.getConfiguration(),
              NULL_PARTITION_ID,
              new SimpleMeterRegistry(),
              InstantSource.system())) {

        ThreadContextUtil.runCheckedWithClassLoader(
            () -> instance.configure(context), instance.getClass().getClassLoader());
      } finally {
        instance.close();
      }
    } catch (final Exception ex) {
      throw new ExporterLoadException(descriptor.getId(), "failed validation", ex);
    }
  }
}
