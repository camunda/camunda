/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.repo;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.context.ExporterContext;
import io.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.zeebe.broker.exporter.jar.ExporterJarRepository;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.Exporter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

public final class ExporterRepository {
  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private final ExporterJarRepository jarRepository;
  private final Map<String, ExporterDescriptor> exporters;

  public ExporterRepository() {
    this(new HashMap<>(), new ExporterJarRepository());
  }

  public ExporterRepository(
      final Map<String, ExporterDescriptor> exporters, final ExporterJarRepository jarRepository) {
    this.exporters = exporters;
    this.jarRepository = jarRepository;
  }

  public Map<String, ExporterDescriptor> getExporters() {
    return Collections.unmodifiableMap(exporters);
  }

  public ExporterDescriptor load(final String id, final Class<? extends Exporter> exporterClass)
      throws ExporterLoadException {
    return load(id, exporterClass, null);
  }

  public ExporterDescriptor load(
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
      throws ExporterLoadException, ExporterJarLoadException {
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

    return load(id, exporterClass, config.getArgs());
  }

  private void validate(final ExporterDescriptor descriptor) throws ExporterLoadException {
    try {
      final Exporter instance = descriptor.newInstance();
      final ExporterContext context = new ExporterContext(LOG, descriptor.getConfiguration());
      instance.configure(context);
    } catch (final Exception ex) {
      throw new ExporterLoadException(descriptor.getId(), "failed validation", ex);
    }
  }
}
