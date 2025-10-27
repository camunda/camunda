/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.camunda.application.commons.metrics.jfr.NativeMemoryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import jdk.jfr.consumer.RecordingStream;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;

/**
 * Allows registering and tracking metrics based on JFR events. Although there should be no overhead
 * according to the JVM, we still allow disabling this via an experimental flag {@code
 * camunda.flags.jfr.metrics = false}.
 *
 * <p>Defaults to being enabled.
 *
 * <p>NOTE: the stream is not closed, as this is a singleton component; it will live until the
 * application is closed. Since it's using a daemon thread, this will not prevent the application
 * from stopping.
 */
// @Component
// @ConditionalOnProperty(prefix = "camunda.flags.jfr", name = "metrics", havingValue = "true")
// @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public final class JfrMetricRecorder {
  private final RecordingStream jfrStream;
  private final MeterRegistry registry;

  // @Autowired
  public JfrMetricRecorder(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a meter registry");
    jfrStream = new RecordingStream();
    jfrStream.setMaxSize(8 * 1024 * 1024);
  }

  @EventListener(classes = {ApplicationStartedEvent.class})
  public void onStart(final ApplicationStartedEvent ignored) {
    new NativeMemoryMetrics().registerEvents(jfrStream, registry);
    jfrStream.startAsync();
  }
}
