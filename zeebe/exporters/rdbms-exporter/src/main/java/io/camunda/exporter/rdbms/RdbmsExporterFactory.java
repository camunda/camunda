/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterInstantiationException;
import io.camunda.zeebe.exporter.api.Exporter;

public class RdbmsExporterFactory implements ExporterFactory {

  private final RdbmsServiceFactory rdbmsServiceFactory;
  private final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry;

  public RdbmsExporterFactory(
      final RdbmsServiceFactory rdbmsServiceFactory,
      final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry) {
    this.rdbmsServiceFactory = rdbmsServiceFactory;
    this.rdbmsSchemaManagerRegistry = rdbmsSchemaManagerRegistry;
  }

  @Override
  public String exporterId() {
    return "rdbms";
  }

  @Override
  public Exporter newInstance() throws ExporterInstantiationException {
    return new RdbmsExporterWrapper(rdbmsServiceFactory, rdbmsSchemaManagerRegistry);
  }

  @Override
  public boolean isSameTypeAs(final ExporterFactory other) {
    return false;
  }
}
