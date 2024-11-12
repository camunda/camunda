/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.micrometer.core.instrument.Counter;

public class ExporterMetricsTestExporter implements Exporter {

  public static final String REGISTERED_COUNTER = "zeebe_exporter_counter";

  public ExporterMetricsTestExporter() {}

  @Override
  public void configure(final Context context) throws Exception {
    Exporter.super.configure(context);
    Counter.builder(REGISTERED_COUNTER).register(context.getMeterRegistry());
  }

  @Override
  public void export(final Record<?> record) {}
}
