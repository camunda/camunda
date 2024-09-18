/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import static java.util.Collections.emptyList;

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
import java.util.Collections;
import java.util.HashMap;
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
  private final Map<String, ExporterFactory> exporterFactoriesMap = new HashMap<>();

  public ExporterRepository() {
    this(new HashMap<>(), new ExternalJarRepository(), emptyList());
  }

  public ExporterRepository(List<ExporterFactory> exporterFactories) {
    this(new HashMap<>(), new ExternalJarRepository(), exporterFactories);
  }

  public ExporterRepository(
      final Map<String, ExporterDescriptor> exporters,
      final ExternalJarRepository jarRepository,
      final List<ExporterFactory> exporterFactories) {
    this.exporters = exporters;
    this.jarRepository = jarRepository;
    if (exporterFactories != null && !exporterFactories.isEmpty()) {
      exporterFactoriesMap.putAll(
          exporterFactories.stream()
              .collect(Collectors.toMap(ExporterFactory::exporterId, Function.identity())));
    }
  }

  public Map<String, ExporterDescriptor> getExporters() {
    return Collections.unmodifiableMap(exporters);
  }

  @VisibleForTesting
  public ExporterDescriptor validateAndAddExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args,
      final ExporterFactory exporterFactory)
      throws ExporterLoadException {
    final ExporterDescriptor descriptor =
        new ExporterDescriptor(id, exporterClass, args, exporterFactory);
    validate(descriptor);
    exporters.put(id, descriptor);
    return descriptor;
  }

  public ExporterDescriptor load(final String id, final ExporterCfg config)
      throws ExporterLoadException, ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends Exporter> exporterClass;

    if (exporters.containsKey(id)) {
      return exporters.get(id);
    }

    if (!exporterFactoriesMap.containsKey(id)) {
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

      return validateAndAddExporterDescriptor(id, exporterClass, config.getArgs(), null);
    } else {
      final ExporterFactory exporterFactory = exporterFactoriesMap.get(id);
      return validateAndAddExporterDescriptor(id, null, config.getArgs(), exporterFactory);
    }
  }

  private void validate(final ExporterDescriptor descriptor) throws ExporterLoadException {
    try {
      final Exporter instance = descriptor.newInstance();
      final ExporterContext context =
          new ExporterContext(
              LOG,
              descriptor.getConfiguration(),
              NULL_PARTITION_ID,
              new SimpleMeterRegistry(),
              InstantSource.system());

      ThreadContextUtil.runCheckedWithClassLoader(
          () -> instance.configure(context), instance.getClass().getClassLoader());
    } catch (final Exception ex) {
      throw new ExporterLoadException(descriptor.getId(), "failed validation", ex);
    }
  }
}
