/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.util;

import io.camunda.zeebe.broker.exporter.repo.ExporterFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterInstantiationException;
import io.camunda.zeebe.exporter.api.Exporter;

public class TestExporterFactory implements ExporterFactory {

  @Override
  public String exporterId() {
    return "controlled";
  }

  @Override
  public Exporter newInstance() throws ExporterInstantiationException {
    return new ControlledTestExporter();
  }
}
