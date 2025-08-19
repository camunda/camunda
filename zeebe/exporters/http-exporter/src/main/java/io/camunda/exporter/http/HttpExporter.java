/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.http.client.ExporterHttpClient;
import io.camunda.exporter.http.config.HttpExporterConfig;
import io.camunda.exporter.http.config.SubscriptionConfig;
import io.camunda.exporter.http.config.SubscriptionConfigFactory;
import io.camunda.exporter.http.subscription.Subscription;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The HttpExporter is a Zeebe Exporter that sends records to a specified HTTP endpoint in batches.
 * A {@link Subscription} uses an {@link ExporterHttpClient} to perform the HTTP requests.
 */
public class HttpExporter implements Exporter {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());
  private Controller controller;
  private SubscriptionConfig subscriptionConfig;
  private Subscription subscription;

  /**
   * Default constructor for the Exporter. This constructor is used when the exporter is
   * instantiated by the Zeebe engine.
   */
  public HttpExporter() {}

  /**
   * Constructor that allows passing a SubscriptionConfig directly. Useful for testing or when the
   * configuration is known at runtime.
   *
   * @param subscriptionConfig the configuration for the subscription
   */
  public HttpExporter(final SubscriptionConfig subscriptionConfig) {
    this.subscriptionConfig = subscriptionConfig;
  }

  @Override
  public void configure(final Context context) {
    log.info("Configuring HTTP Exporter");
    final var configFactory = new SubscriptionConfigFactory(objectMapper);

    if (subscriptionConfig == null) {
      log.debug(
          "Subscription config not provided in constructor, loading from configuration file.");
      final var httpExporterConfiguration =
          context.getConfiguration().instantiate(HttpExporterConfig.class);
      subscriptionConfig = configFactory.readConfigFrom(httpExporterConfiguration);
    }

    subscription = configFactory.createSubscription(subscriptionConfig);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    if (subscription == null) {
      throw new IllegalStateException(
          "Subscription must be configured before opening the exporter.");
    }
    controller.scheduleCancellableTask(
        Duration.ofMillis(subscriptionConfig.batchInterval()), this::attemptFlushAndReschedule);
  }

  @Override
  public void close() {
    log.info("Closing HTTP Exporter");
    if (subscription != null) {
      subscription.close();
    }
  }

  @Override
  public void export(final Record<?> record) {
    updateExportPosition(subscription.exportRecord(record));
  }

  @Override
  public void purge() {
    log.info("Purging HTTP Exporter");
  }

  protected Subscription getSubscription() {
    return subscription;
  }

  private void attemptFlushAndReschedule() {
    try {
      log.debug("Attempting to flush HTTP Exporter from background task");
      updateExportPosition(subscription.attemptFlush());
    } catch (final Throwable e) {
      log.warn("Error during flush. Will retry with next attempt.", e);
    } finally {
      scheduleDelayedFlush();
    }
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofMillis(subscriptionConfig.batchInterval()), this::attemptFlushAndReschedule);
  }

  private void updateExportPosition(final Long position) {
    if (position != null) {
      controller.updateLastExportedRecordPosition(position);
    }
  }
}
