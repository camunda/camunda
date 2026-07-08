/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.pipeline.stage.AnalyticsAggregationStage;
import io.camunda.analytics.pipeline.stage.AnalyticsPipelineConfig;
import io.camunda.analytics.pipeline.stage.AnalyticsProjectionStage;
import io.camunda.analytics.pipeline.stage.PipelineRuntimes;
import io.camunda.analytics.pipeline.stage.PipelineRuntimes.RunningPipeline;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Runs the analytics pipeline <em>inside</em> the serving application, so a single deployable is
 * the whole analytics node: it serves the dashboard read API and drives both ingest stages (Stage 1
 * base projection → facts topic, Stage 2 facts → serving sink) in one JVM. A Spring {@link
 * SmartLifecycle} bean, so the stages start after the context is up and stop first on shutdown
 * (their final commit still uses the shared client/scheduler, which {@link RunningPipeline#close}
 * tears down last); the two stages share one {@link EventBridgeClient}, {@link ActorScheduler}, and
 * sink executor via {@link PipelineRuntimes}.
 *
 * <p>Gated by {@code analytics.pipeline.enabled} (default {@code true}) — set it {@code false} to
 * run a serving-only node (the seed of stage/serving role separation). Scaling out is running more
 * instances: each node's stage runtimes join the same event-bridge consumer groups, so partitions
 * rebalance across the cluster, while every node serves reads off the shared serving store.
 *
 * <p>Start is fail-fast: building the stages provisions the facts topic and metadata plane, so the
 * event-bridge gateway must be reachable at boot (resilient start/retry is a later refinement).
 */
@Component
@ConditionalOnProperty(
    name = "analytics.pipeline.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AnalyticsPipelineLifecycle implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsPipelineLifecycle.class);

  private RunningPipeline pipeline;

  @Override
  public synchronized void start() {
    if (pipeline != null) {
      return;
    }
    final AnalyticsPipelineConfig config = AnalyticsPipelineConfig.fromSystemProperties("stage1");
    final EventBridgeClient client = PipelineRuntimes.newClient();
    final ActorScheduler scheduler = PipelineRuntimes.startScheduler();
    // Both stages' cuts share this pool, so it is sized by source + facts partitions.
    final ExecutorService sinkExecutor =
        PipelineRuntimes.newSinkExecutor(
            PipelineRuntimes.topicPartitions(client, config.sourceTopic())
                + config.factsPartitions());
    final StreamRuntime<SourceRecord> stage1 =
        AnalyticsProjectionStage.buildRuntime(client, scheduler, sinkExecutor);
    final StreamRuntime<ShuffleEnvelope> stage2 =
        AnalyticsAggregationStage.buildRuntime(client, scheduler, sinkExecutor);
    LOG.info("Starting the analytics pipeline in-process: Stage 1 + Stage 2 on a shared scheduler");
    pipeline =
        PipelineRuntimes.start(
            client,
            scheduler,
            sinkExecutor,
            List.of(
                new PipelineRuntimes.Member("stage1", stage1),
                new PipelineRuntimes.Member("stage2", stage2)));
  }

  @Override
  public synchronized void stop() {
    if (pipeline != null) {
      LOG.info("Stopping the analytics pipeline");
      pipeline.close();
      pipeline = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return pipeline != null;
  }
}
