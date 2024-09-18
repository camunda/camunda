/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

public class ExporterDescriptor {
  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private final ExporterConfiguration configuration;
  private final Class<? extends Exporter> exporterClass;
  private final ExporterFactory exporterFactory;

  public ExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args) {
    this(id, exporterClass, args, null);
  }

  public ExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args,
      final ExporterFactory exporterFactory) {
    this.exporterClass = exporterClass;
    this.configuration = new ExporterConfiguration(id, args);
    this.exporterFactory =
        Objects.requireNonNullElseGet(exporterFactory, () -> getDefaultExporterFactory(id));
  }

  public Exporter newInstance() throws ExporterInstantiationException {
    return exporterFactory.newInstance();
  }

  public ExporterConfiguration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return configuration.id();
  }

  public boolean isSameTypeAs(final ExporterDescriptor other) {
    return exporterClass.equals(other.exporterClass);
  }

  private ExporterFactory getDefaultExporterFactory(String id) {
    return new ExporterFactory() {
      @Override
      public String exporterId() {
        return id;
      }

      @Override
      public Exporter newInstance() throws ExporterInstantiationException {
        LOG.info("Use default exporter factory to create instance of {}", exporterClass);
        try {
          return ReflectUtil.newInstance(exporterClass);
        } catch (final Exception e) {
          throw new ExporterInstantiationException(getId(), e);
        }
      }
    };
  }
}
