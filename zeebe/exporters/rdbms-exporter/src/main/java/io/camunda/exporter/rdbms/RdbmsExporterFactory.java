/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.zeebe.broker.exporter.repo.ExporterFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterInstantiationException;
import io.camunda.zeebe.exporter.api.Exporter;

public class RdbmsExporterFactory implements ExporterFactory {

  private final RdbmsService rdbmsService;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsSchemaManager rdbmsSchemaManager;

  public RdbmsExporterFactory(
      final RdbmsService rdbmsService,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsSchemaManager rdbmsSchemaManager) {
    this.rdbmsService = rdbmsService;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.rdbmsSchemaManager = rdbmsSchemaManager;
  }

  @Override
  public String exporterId() {
    return "rdbms";
  }

  @Override
  public Exporter newInstance() throws ExporterInstantiationException {
    return new RdbmsExporterWrapper(rdbmsService, rdbmsSchemaManager, vendorDatabaseProperties);
  }

  @Override
  public boolean isSameTypeAs(final ExporterFactory other) {
    return false;
  }
}
