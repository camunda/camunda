/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.ingest;

import io.camunda.analytics.lake.LakePocApp;
import io.camunda.analytics.lake.serving.config.LakeIngestProperties;
import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Hosts the lake ingest engine (consume &rarr; fold &rarr; commit &rarr; compact, see {@link
 * LakePocApp#start}) inside this serving application — the one-backend deployment shape. Only
 * present when {@code lake.serving.ingest.enabled=true}; every test and the default configuration
 * run the pure read layer, so nothing here can affect them.
 *
 * <p>Ingest writes to the same warehouse this application serves (see {@link
 * LakeIngestProperties}'s javadoc for why the directory is not configurable twice). After the
 * engine starts — which creates any missing tables — the {@link LakeViewRegistry} is refreshed
 * once, so a first boot against an empty warehouse serves the freshly created (empty) views
 * immediately instead of waiting for a manual {@code POST /api/refresh}.
 *
 * <p>Lifecycle: starts in an early {@link SmartLifecycle} phase (before the web server accepts
 * traffic) and therefore stops late (after the web server has stopped serving), so requests never
 * observe a half-started or half-drained engine. {@link #stop()} delegates to {@link
 * LakePocApp.Handle#close()}, which drains and closes in the engine's own documented order and is
 * idempotent.
 */
@Component
@ConditionalOnProperty(name = "lake.serving.ingest.enabled", havingValue = "true")
public class LakeIngestLifecycle implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(LakeIngestLifecycle.class);

  /** Starts before (and stops after) the web server's own {@code WebServerStartStopLifecycle}. */
  private static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 2048;

  private final LakeServingProperties servingProperties;
  private final LakeIngestProperties ingestProperties;
  private final LakeViewRegistry viewRegistry;
  private volatile LakePocApp.Handle handle;

  public LakeIngestLifecycle(
      final LakeServingProperties servingProperties,
      final LakeIngestProperties ingestProperties,
      final LakeViewRegistry viewRegistry) {
    this.servingProperties = servingProperties;
    this.ingestProperties = ingestProperties;
    this.viewRegistry = viewRegistry;
  }

  @Override
  public synchronized void start() {
    if (handle != null) {
      return;
    }
    LOG.info(
        "Starting hosted lake ingest (contactPoint={}, topic={}, group={}, warehouse={})",
        ingestProperties.contactPoint(),
        ingestProperties.topic(),
        ingestProperties.group(),
        servingProperties.warehouseDirPath());
    handle = LakePocApp.start(ingestProperties.toLakeConfig(servingProperties.warehouseDirPath()));
    viewRegistry.refresh();
  }

  @Override
  public synchronized void stop() {
    final LakePocApp.Handle current = handle;
    if (current == null) {
      return;
    }
    handle = null;
    LOG.info("Stopping hosted lake ingest (draining)");
    current.close();
  }

  @Override
  public boolean isRunning() {
    return handle != null;
  }

  @Override
  public int getPhase() {
    return PHASE;
  }
}
