/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.metrics;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class SchemaManagerMetrics {
  private static final String NAMESPACE = "camunda.schema";

  private final MeterRegistry meterRegistry;

  private final Timer schemaInitTimer;

  public SchemaManagerMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    schemaInitTimer =
        Timer.builder(NAMESPACE + ".init.time")
            .description("Duration of initializing the schema in the secondary storage")
            .register(meterRegistry);
  }

  public CloseableSilently startSchemaInitTimer() {
    return MicrometerUtil.timer(schemaInitTimer, Timer.start(meterRegistry));
  }
}
