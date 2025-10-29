/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.util.ReflectUtil;
import org.slf4j.Logger;

class DefaultExporterFactory implements ExporterFactory {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;
  private final Class<? extends Exporter> exporterClass;
  private final String id;

  public DefaultExporterFactory(final String id, final Class<? extends Exporter> exporterClass) {
    this.id = id;
    this.exporterClass = exporterClass;
  }

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
      throw new ExporterInstantiationException(id, e);
    }
  }

  @Override
  public boolean isSameTypeAs(final ExporterFactory other) {
    return other instanceof DefaultExporterFactory
        && ((DefaultExporterFactory) other).exporterClass.equals(exporterClass);
  }
}
