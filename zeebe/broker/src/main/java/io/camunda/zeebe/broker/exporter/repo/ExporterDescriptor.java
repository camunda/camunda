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
import java.util.Map;

public class ExporterDescriptor {

  private final ExporterConfiguration configuration;
  private final ExporterFactory exporterFactory;

  public ExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args) {
    this(id, new DefaultExporterFactory(id, exporterClass), args);
  }

  public ExporterDescriptor(
      final String id, final ExporterFactory exporterFactory, final Map<String, Object> args) {
    configuration = new ExporterConfiguration(id, args);
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
    // todo
    return exporterFactory.equals(other.exporterFactory);
  }
}
