/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.api.Exporter;
import java.util.HashMap;
import java.util.Map;

/**
 * When initialising the ExporterRepository via Spring, we inject predefined beans of the
 * ExporterDescriptor into it. So for custom exporters which needs Spring, you simply have to
 * provide an instance of the ExporterDescriptor as spring bean.
 */
public class ExporterDescriptor {
  private final ExporterConfiguration configuration;
  private final ExporterFactory exporterFactory;

  public ExporterDescriptor(final String id) {
    this(id, new DefaultExporterFactory(id, null), new HashMap<>());
  }

  public ExporterDescriptor(final String id, final ExporterFactory exporterFactory) {
    this(id, exporterFactory, new HashMap<>());
  }

  public ExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args) {
    this(id, new DefaultExporterFactory(id, exporterClass), args);
  }

  public ExporterDescriptor(
      final String id, final ExporterFactory exporterFactory, final Map<String, Object> args) {
    this.configuration = new ExporterConfiguration(id, args);
    this.exporterFactory = exporterFactory;
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
    return exporterFactory.isSameTypeAs(other.exporterFactory);
  }
}
