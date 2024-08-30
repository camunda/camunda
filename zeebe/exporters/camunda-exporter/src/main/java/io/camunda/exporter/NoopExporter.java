/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;

public class NoopExporter implements Exporter {
  private NoopExporterConfiguration configuration;
  private SearchEngineClient searchEngineClient;
  private SchemaManager schemaManager;

  @Override
  public void configure(final Context context) throws Exception {
    Exporter.super.configure(context);
    configuration = context.getConfiguration().instantiate(NoopExporterConfiguration.class);
  }

  @Override
  public void open(final Controller controller) {
    Exporter.super.open(controller);
  }

  @Override
  public void close() {
    Exporter.super.close();
  }

  @Override
  public void export(final Record<?> record) {
    System.out.println("Your wish to export has been granted!");
  }
}
