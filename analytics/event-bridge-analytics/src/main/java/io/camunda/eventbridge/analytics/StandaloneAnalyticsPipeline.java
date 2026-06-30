/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.eventbridge.analytics.aggregate.DatasetRegistry;
import io.camunda.eventbridge.analytics.aggregate.WindowedExecutionTimeAggregator;
import io.camunda.eventbridge.analytics.projection.ProcessInstanceProjector;
import io.camunda.eventbridge.analytics.projection.RocksDbBaseProjectionStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Phase 1 of the consumer-based analytics library as a standalone process against a running
 * Event Bridge: consume {@code zeebe-records}, fold into a RocksDB base projection, and aggregate
 * execution-time facts into a windowed H2 dataset (DB-as-merge — no fact-topic, no shuffle, no
 * coordinator). The headline metric is "N instances completed per definition per window".
 *
 * <p><b>Multi-instance:</b> start several copies with the same group ({@code analytics-projection})
 * and distinct {@code -DinstanceId}s — the bridge consumer group assigns source partitions across
 * them and rebalances on membership change. Each instance keeps its own local RocksDB projection
 * (per-instance {@code data/} dir) and merges into the shared dataset. For more than one instance
 * the dataset must be a <em>shared</em> RDBMS — pass {@code -DjdbcUrl} pointing at an H2 server or
 * other RDBMS (the default file H2 is single-process, fine for one instance / a demo).
 *
 * <p>System properties: {@code gateway} (default {@code http://localhost:8080}), {@code
 * sourceTopic} ({@code zeebe-records}), {@code instanceId} (default = PID), {@code windowSizeMs}
 * (default 1h), {@code jdbcUrl} (default file H2).
 */
public final class StandaloneAnalyticsPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneAnalyticsPipeline.class);
  private static final String GROUP = "analytics-projection";

  private StandaloneAnalyticsPipeline() {}

  public static void main(final String[] args) throws InterruptedException {
    final String gateway = System.getProperty("gateway", "http://localhost:8080");
    final String sourceTopic = System.getProperty("sourceTopic", "zeebe-records");
    final String instanceId =
        System.getProperty("instanceId", "projector-" + ProcessHandle.current().pid());
    final String jdbcUrl =
        System.getProperty("jdbcUrl", "jdbc:h2:file:./data/analytics-dataset;DB_CLOSE_DELAY=-1");
    // user 'sa' so the dataset DB has consistent credentials with the webapp / shared H2 server
    // (e.g. jdbc:h2:tcp://localhost:9092/analytics-dataset for live UI data).
    final String jdbcUser = System.getProperty("jdbcUser", "sa");

    final EventBridgeClient client = EventBridgeClient.create(gateway);

    final RocksDbBaseProjectionStore store =
        RocksDbBaseProjectionStore.open(
            new File("data/analytics-projection-" + instanceId), new SimpleMeterRegistry());
    final ProcessInstanceProjector projector = new ProcessInstanceProjector(store);

    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(jdbcUrl);
    dataSource.setUser(jdbcUser);
    final WindowedExecutionTimeAggregator aggregator =
        new WindowedExecutionTimeAggregator(dataSource);
    aggregator.initSchema();
    // datasets (and their windows) are declared via the webapp into the shared DB; the pipeline
    // reads them and aggregates each fact into every declared dataset.
    final DatasetRegistry datasetRegistry = new DatasetRegistry(dataSource);

    final ZeebeRecordConsumer source =
        ZeebeRecordConsumer.subscribe(client, GROUP, instanceId, List.of(sourceTopic)).join();

    final WindowedAnalyticsPipeline pipeline =
        new WindowedAnalyticsPipeline(source, projector, aggregator, datasetRegistry);
    pipeline.start();
    LOG.info(
        "Phase-1 analytics instance '{}' started: {} -> per-dataset windowed H2 aggregates",
        instanceId,
        sourceTopic);

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
