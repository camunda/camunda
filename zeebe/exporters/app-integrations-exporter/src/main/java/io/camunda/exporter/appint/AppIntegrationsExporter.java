/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint;

import io.camunda.exporter.appint.config.Config;
import io.camunda.exporter.appint.event.Event;
import io.camunda.exporter.appint.subscription.Subscription;
import io.camunda.exporter.appint.subscription.SubscriptionFactory;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exporter for the App Integrations component. */
public class AppIntegrationsExporter implements Exporter {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());

  private Subscription<Event> subscription;
  private Controller controller;
  private Config config;

  @Override
  public void configure(final Context context) {
    config = context.getConfiguration().instantiate(Config.class);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    subscription = SubscriptionFactory.createDefault(config, this::updateExportPosition);
    scheduleDelayedFlush();
  }

  @Override
  public void close() {
    if (subscription != null) {
      subscription.close();
    }
  }

  @Override
  public void export(final Record<?> record) {
    try {
      subscription.exportRecord(record);
    } catch (final Exception e) {
      log.debug("Exception during export of record: {}", record.getPosition(), e);
      throw e;
    }
  }

  private void attemptFlushAndReschedule() {
    try {
      log.debug("Attempting to flush exporter from background task");
      subscription.attemptFlush();
    } catch (final Throwable e) {
      log.warn("Error during flush. Will retry with next attempt.", e);
    } finally {
      scheduleDelayedFlush();
    }
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofMillis(config.getBatchIntervalMs()), this::attemptFlushAndReschedule);
  }

  private void updateExportPosition(final Long position) {
    if (position != null) {
      controller.updateLastExportedRecordPosition(position);
    }
  }

  public Subscription<Event> getSubscription() {
    return subscription;
  }
}
