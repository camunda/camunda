/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.eventbridge.analytics.aggregate.ExecutionTimeAggregator;
import io.camunda.eventbridge.analytics.fact.EventBridgeFactPublisher;
import io.camunda.eventbridge.analytics.fact.FactSink;
import io.camunda.eventbridge.analytics.projection.ProcessInstanceProjector;
import io.camunda.eventbridge.analytics.projection.RocksDbBaseProjectionStore;
import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the analytics pipeline as a standalone process against a running Event Bridge: it consumes
 * {@code zeebe-records}, folds them into a RocksDB base projection, publishes execution-time facts
 * to a fact topic, and aggregates them into an H2 dataset.
 *
 * <p>This is the runnable validation path for the pipeline. Embedding the same stages into the
 * data-partition lifecycle (so leadership/HA come from the source partition's Raft) is the
 * productionization step tracked in {@code docs/analytics-pipeline-mvp-handover.md}.
 *
 * <p>System properties: {@code gateway} (default {@code http://localhost:8080}), {@code
 * sourceTopic} ({@code zeebe-records}), {@code factTopic} ({@code analytics-facts}), {@code
 * factPartitions} (1).
 */
public final class StandaloneAnalyticsPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneAnalyticsPipeline.class);

  private StandaloneAnalyticsPipeline() {}

  public static void main(final String[] args) throws InterruptedException {
    final String gateway = System.getProperty("gateway", "http://localhost:8080");
    final String sourceTopic = System.getProperty("sourceTopic", "zeebe-records");
    final String factTopic = System.getProperty("factTopic", "analytics-facts");
    final int factPartitions = Integer.getInteger("factPartitions", 1);

    final EventBridgeClient client = EventBridgeClient.create(gateway);
    try {
      client.createTopic(factTopic, factPartitions, 1).join();
    } catch (final RuntimeException e) {
      LOG.info("Fact topic '{}' may already exist: {}", factTopic, e.getMessage());
    }

    final RocksDbBaseProjectionStore store =
        RocksDbBaseProjectionStore.open(
            new File("data/analytics-projection"), new SimpleMeterRegistry());
    final ProcessInstanceProjector projector = new ProcessInstanceProjector(store);
    final FactSink factSink = new EventBridgeFactPublisher(client, factTopic, factPartitions);

    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:file:./data/analytics-dataset;DB_CLOSE_DELAY=-1");
    final ExecutionTimeAggregator aggregator = new ExecutionTimeAggregator(dataSource);
    aggregator.initSchema();

    final ZeebeRecordConsumer source =
        ZeebeRecordConsumer.subscribe(
                client, "analytics-projection", "projector-1", List.of(sourceTopic))
            .join();
    final Consumer factConsumer =
        client.subscribe("analytics-aggregation", "aggregator-1", List.of(factTopic)).join();

    final AnalyticsPipeline pipeline =
        new AnalyticsPipeline(source, projector, factSink, factConsumer, aggregator);
    pipeline.start();
    LOG.info("Analytics pipeline started: {} -> {} -> H2 dataset", sourceTopic, factTopic);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  pipeline.close();
                  try {
                    store.close();
                    client.close();
                  } catch (final Exception ignored) {
                    // shutting down
                  }
                }));
    Thread.currentThread().join();
  }
}
