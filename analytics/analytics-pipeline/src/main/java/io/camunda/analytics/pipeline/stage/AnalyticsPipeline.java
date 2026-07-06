/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidated launcher: runs both pipeline stages in one JVM on a single shared actor scheduler
 * and sink IO executor, instead of two processes each spinning up their own pools. Stage 1 (base
 * projection → facts topic) and Stage 2 (facts topic → serving sink) each get their own {@link
 * StreamRuntime} and consumer group, but their partition actors are multiplexed onto the one shared
 * cpu-bound pool and their blocking commits onto the one shared sink pool — so the node's thread
 * footprint is bounded by the shared pools, not by the number of stages.
 *
 * <p>The individual {@link AnalyticsProjectionStage} / {@link AnalyticsAggregationStage} mains
 * still run a single stage standalone; this launcher just shares the pools across both.
 */
public final class AnalyticsPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsPipeline.class);

  private AnalyticsPipeline() {}

  public static void main(final String[] args) {
    final ActorScheduler scheduler = PipelineRuntimes.startScheduler();
    final ExecutorService sinkExecutor = PipelineRuntimes.newSinkExecutor();

    final StreamRuntime<SourceRecord> stage1 =
        AnalyticsProjectionStage.buildRuntime(scheduler, sinkExecutor);
    final StreamRuntime<ShuffleEnvelope> stage2 =
        AnalyticsAggregationStage.buildRuntime(scheduler, sinkExecutor);

    LOG.info("Analytics pipeline: running Stage 1 + Stage 2 on a shared actor scheduler");
    PipelineRuntimes.run(
        scheduler,
        sinkExecutor,
        List.of(
            new PipelineRuntimes.Member("stage1", stage1),
            new PipelineRuntimes.Member("stage2", stage2)));
  }
}
