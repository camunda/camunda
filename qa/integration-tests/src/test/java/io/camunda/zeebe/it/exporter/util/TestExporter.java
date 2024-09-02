/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter.util;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;

/**
 * A test exporter used by {@link io.camunda.zeebe.it.exporter.ExporterConfigInstantiationIT}. It
 * needs to be a top level class since we'll be rebasing (i.e. merging) with a dynamically created
 * implementation class before packaging it as a JAR, along with its companion class {@link
 * TestExporterConfig}.
 *
 * <p>Doing this allows us to define an implementation of a fully standalone exporter that can be
 * packaged dynamically during a test into a JAR, and injected into a container.
 */
public class TestExporter implements Exporter {

  @Override
  public void configure(final Context context) throws Exception {
    final var config = context.getConfiguration().instantiate(TestExporterConfig.class);
    if (config.strings().isEmpty()) {
      throw new IllegalStateException("Need to specify some strings");
    }
  }

  @Override
  public void export(final Record<?> record) {}
}
