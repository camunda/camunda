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
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SchemaManagerMetrics {
  public static final SchemaManagerMetrics DEFAULT =
      new SchemaManagerMetrics(new SimpleMeterRegistry());

  private static final String NAMESPACE = "camunda.schema";

  private final MeterRegistry meterRegistry;

  private final AtomicLong schemaInitTime = new AtomicLong();

  public SchemaManagerMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    TimeGauge.builder(
            NAMESPACE + ".init.time", schemaInitTime, TimeUnit.MILLISECONDS, Number::longValue)
        .description("Duration of init schema operations (in milliseconds)")
        .register(meterRegistry);
  }

  public CloseableSilently startSchemaInitTimer() {
    return MicrometerUtil.timer(
        schemaInitTime::set, TimeUnit.MILLISECONDS, meterRegistry.config().clock());
  }
}
