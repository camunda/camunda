/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.eventbridge.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.streaming.aggregate.MergingRollup;
import io.camunda.eventbridge.streaming.shuffle.Partial;
import io.camunda.eventbridge.streaming.shuffle.PartialCodec;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 2 of the staged pipeline: consume the facts topic (partials produced by Stage-1 combiners,
 * partitioned by grouping key so each cell has a single owner), merge them per {@code aggId} into
 * the global windowed aggregate via a {@link MergingRollup} (per-writer slots), and converge the
 * idempotent serving sink — so dashboard reads hit one cell (O(1)).
 *
 * <p>This class only <em>wires</em> the stage: a {@link MergeStage} dispatches each partial to its
 * merger, and a {@link StreamRuntime} owns the poll loop, restore, and the commit barrier — the
 * slots + the facts-topic offset commit as one atomic cut, then the coordinator offset last.
 */
public final class AnalyticsAggregationStage {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsAggregationStage.class);
  private static final int MAX_RECORDS = 5000;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration CHECKPOINT_INTERVAL =
      Duration.ofMillis(Long.getLong("analytics.checkpointIntervalMs", 1000L));
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private AnalyticsAggregationStage() {}

  public static void main(final String[] args) {
    final String group = System.getProperty("group", "analytics-stage2");
    final String gateway = System.getProperty("gateway", "http://localhost:8080");
    final String factsTopic = System.getProperty("factsTopic", "analytics-facts");
    final String instanceId =
        System.getProperty("instanceId", "stage2-" + ProcessHandle.current().pid());
    final String jdbcUrl =
        System.getProperty("jdbcUrl", "jdbc:h2:file:./data/analytics-dataset;DB_CLOSE_DELAY=-1");
    final String jdbcUser = System.getProperty("jdbcUser", "sa");
    final long slaMs = Long.getLong("slaMs", 300_000L);

    final EventBridgeClient client = EventBridgeClient.create(gateway);
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(jdbcUrl);
    dataSource.setUser(jdbcUser);

    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(
            new File("data/analytics-stage2-" + instanceId), meterRegistry);
    final KeyValueStore<DbBytes, DbBytes> slotStore =
        provider.keyValueStore(AnalyticsColumnFamilies.SLOT_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> factsOffsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());

    final Map<Integer, MergingRollup<?, ?>> mergers = new HashMap<>();
    for (final MetricSpec<?, ?, ?> spec : Metrics.specs(slaMs)) {
      mergers.put(
          spec.aggId(),
          StageBuilders.merger(spec, slotStore, dataSource, provider::runInTransaction));
    }

    final StreamProcessor<Partial> task =
        new StreamProcessor<Partial>().add(new MergeStage(mergers));

    final StreamRuntime<Partial> runtime =
        StreamRuntime.<Partial>builder()
            .client(client)
            .group(group)
            .instanceId(instanceId)
            .sourceTopic(factsTopic)
            .deserializer((payload, partition, offset) -> PartialCodec.decode(payload))
            .taskFactory(partition -> task)
            .transactionRunner(provider::runInTransaction)
            .offsetStore(new KeyValueOffsetStore(factsOffsets))
            .maxPoll(MAX_RECORDS)
            .pollTimeout(POLL_TIMEOUT)
            .commitInterval(CHECKPOINT_INTERVAL)
            .errorBackoff(ERROR_BACKOFF)
            .build();

    LOG.info("Analytics Stage 2 '{}': {} -> mergers -> serving sink", instanceId, factsTopic);
    Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop, "stage2-shutdown"));
    runtime.run();
  }
}
