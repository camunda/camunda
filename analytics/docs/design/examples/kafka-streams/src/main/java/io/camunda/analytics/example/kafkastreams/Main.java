/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

import java.time.Duration;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point: assembles the config and starts the topology.
 *
 * <p>This class is where the cross-cutting guarantees are configured — checkpointing cadence and
 * exactly-once. The topology itself (what to compute) is in {@link AnalyticsTopology}; this file is
 * how the runtime is told to run it.
 */
public final class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static void main(final String[] args) {
    final Properties props = new Properties();

    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "process-instance-analytics");
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

    // Default serdes; the topology overrides them explicitly at every operator anyway.
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Long().getClass());
    props.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());

    // --- EXACTLY-ONCE --------------------------------------------------------------------------
    // exactly_once_v2 turns on Kafka's transactional processing: for each commit, the consumed source
    // offsets, every write to changelog/repartition/output topics, and the state-store updates are
    // committed together in ONE Kafka transaction. On failure the whole transaction aborts and is
    // replayed, so no partial effects are ever observed downstream. v2 (vs the original EOS) uses a
    // single producer per instance with consumer-group-aware transactions, so it scales to many
    // partitions cheaply. This is the entire exactly-once story in ONE config line.
    props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);

    // --- CHECKPOINTING -------------------------------------------------------------------------
    // The commit interval IS the checkpoint cadence: at each commit the transaction above is closed
    // and a new one opened. Smaller interval → tighter latency + more frequent durability, but more
    // transaction overhead. Under EOS this also bounds how much work is replayed after a crash.
    props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, (int) Duration.ofSeconds(10).toMillis());

    // Local state directory: RocksDB state stores + their offset checkpoints live here. The changelog
    // topics are the durable source of truth; this dir is a warm cache that avoids a full restore.
    props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams-analytics-example");

    // Standby replicas: hot copies of each store's changelog on other instances, so a failover does
    // not have to replay the whole changelog before resuming. Pure availability tuning.
    props.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1);

    // The forward-only gate boundary. In a real deployment this would be loaded from the serving
    // store's high-water mark on startup so re-consumption from an earlier offset does not
    // double-count already-served facts.
    final long activationOffset = Long.getLong("analytics.activationOffset", 0L);

    final Topology topology = new AnalyticsTopology(activationOffset).build();
    LOG.info("Topology:\n{}", topology.describe());

    final KafkaStreams streams = new KafkaStreams(topology, props);
    streams.setUncaughtExceptionHandler(
        e -> {
          LOG.error("Uncaught streams exception; replacing the thread", e);
          return StreamThreadExceptionResponse.REPLACE_THREAD;
        });

    Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    streams.start();
  }

  private Main() {}
}
