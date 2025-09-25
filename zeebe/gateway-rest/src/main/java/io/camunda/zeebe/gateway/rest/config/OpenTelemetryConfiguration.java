/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfiguration {
//  @Bean
//  public TextMapPropagator textMapPropagator() {
//    return W3CTraceContextPropagator.getInstance();
//  }
//
//  @Bean
//  public SpanExporter loggingSpanExporter() {
//    return LoggingSpanExporter.create();
//  }
//
//  @Bean
//  public SpanProcessor loggingSpanProcessor(final SpanExporter loggingSpanExporter) {
//    return SimpleSpanProcessor.create(loggingSpanExporter);
//  }
}
