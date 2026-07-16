/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.config;

import io.camunda.eventbridge.core.topic.AutoCreatedTopic;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Top-level Spring Boot configuration properties for the Event Bridge component. Bind with the
 * prefix {@code event-bridge}.
 */
@ConfigurationProperties(prefix = "event-bridge")
public record EventBridgeProperties(
    DataProperties data,
    BrokerProperties broker,
    CoordinatorProperties coordinator,
    ConsumerProperties consumer,
    PublishProperties publish,
    RetentionProperties retention,
    RaftProperties raft,
    ClusterProperties cluster,
    List<TopicProperties> topics,
    RebalanceProperties rebalance) {

  public EventBridgeProperties {
    if (data == null) {
      data = new DataProperties("data");
    }
    if (topics == null) {
      topics = List.of();
    }
    if (rebalance == null) {
      rebalance = new RebalanceProperties(true, 5_000, 2);
    }
    if (broker == null) {
      broker =
          new BrokerProperties(
              1,
              new SnapshotProperties(1000),
              new LongPollProperties(30_000),
              new TruncationProperties(60_000));
    }
    if (coordinator == null) {
      coordinator = new CoordinatorProperties("broker-0", 30_000, 1);
    }
    if (consumer == null) {
      consumer = new ConsumerProperties(10_000, 2_000, 5_000, 100, 1_000, 1);
    }
    if (publish == null) {
      publish = new PublishProperties(1_000, 1_048_576, 10_485_760);
    }
    if (retention == null) {
      retention = new RetentionProperties(1_000_000, 60_000, 32 * 1024 * 1024);
    }
    if (raft == null) {
      raft = new RaftProperties(1);
    }
    if (cluster == null) {
      cluster =
          new ClusterProperties(
              "event-bridge", "broker-0", "0.0.0.0", 26502, 1, null, null, 26501, List.of());
    }
  }

  /**
   * The configured topics to auto-create on startup, fully resolved: each entry's partition count
   * and replication factor default to {@code broker.partitionCount} and {@code
   * raft.replicationFactor} respectively when not set explicitly. The metadata leader creates these
   * idempotently, so they are provisioned once and survive restarts.
   */
  public List<AutoCreatedTopic> resolvedTopics() {
    return topics.stream()
        .map(
            t ->
                new AutoCreatedTopic(
                    t.name(),
                    t.partitionCount() != null ? t.partitionCount() : broker.partitionCount(),
                    t.replicationFactor() != null
                        ? t.replicationFactor()
                        : raft.replicationFactor(),
                    t.cleanupPolicy() != null ? t.cleanupPolicy() : "DELETE"))
        .toList();
  }

  /**
   * A topic to auto-create on startup, bound from {@code event-bridge.topics}.
   *
   * @param name the topic name (required; must match {@code [a-zA-Z0-9._-]{1,249}})
   * @param partitionCount number of partitions; defaults to {@code broker.partitionCount} when
   *     unset
   * @param replicationFactor number of replicas per partition; defaults to {@code
   *     raft.replicationFactor} when unset
   * @param cleanupPolicy retention policy, {@code "DELETE"} or {@code "COMPACT"} (event-bridge ADR
   *     0001); defaults to {@code "DELETE"} — today's behavior — when unset
   */
  public record TopicProperties(
      String name, Integer partitionCount, Integer replicationFactor, String cleanupPolicy) {}

  /**
   * Auto-rebalance configuration. When enabled, the metadata leader incrementally spreads each
   * topic's replicas across the active brokers as the cluster grows — one minimal-diff move at a
   * time, behind a quiescence gate (never a big-bang whole-topic reassignment).
   *
   * @param enabled whether the auto-rebalance controller runs at all (default true)
   * @param intervalMs how often (ms) the controller evaluates balance and, if quiescent, issues the
   *     next single move (default 5000)
   * @param minImbalance the smallest replica-count gap between the most- and least-loaded broker
   *     worth a move; balance settles within {@code minImbalance - 1} (default 2, i.e. balance to a
   *     gap of at most 1)
   */
  public record RebalanceProperties(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("5000") long intervalMs,
      @DefaultValue("2") int minImbalance) {}

  /**
   * Data directory configuration.
   *
   * @param directory root directory for RAFT partition storage (default "data")
   */
  public record DataProperties(@DefaultValue("data") String directory) {}

  /**
   * Broker-specific configuration.
   *
   * @param partitionCount number of partitions this broker manages (default 1)
   * @param snapshot snapshot trigger configuration
   * @param longPoll long-poll ceiling configuration
   * @param truncation log truncation timer configuration
   */
  public record BrokerProperties(
      @DefaultValue("3") int partitionCount,
      SnapshotProperties snapshot,
      LongPollProperties longPoll,
      TruncationProperties truncation) {

    public BrokerProperties {
      if (snapshot == null) {
        snapshot = new SnapshotProperties(1000);
      }
      if (longPoll == null) {
        longPoll = new LongPollProperties(30_000);
      }
      if (truncation == null) {
        truncation = new TruncationProperties(60_000);
      }
    }
  }

  /**
   * Snapshot trigger configuration.
   *
   * @param intervalEntryCount number of RAFT log entries (batches) between automatic snapshots
   *     (default 1000; valid range [100, 100000])
   */
  public record SnapshotProperties(@DefaultValue("1000") int intervalEntryCount) {}

  /**
   * Long-poll ceiling.
   *
   * @param maxWaitMs maximum server-side wait time in milliseconds a client may request (default
   *     30000; values above this ceiling are clamped silently)
   */
  public record LongPollProperties(@DefaultValue("30000") int maxWaitMs) {}

  /**
   * Log truncation timer.
   *
   * @param intervalMs how often (in milliseconds) the coordinator re-evaluates the truncation
   *     low-watermark for each partition (default 60000)
   */
  public record TruncationProperties(@DefaultValue("60000") long intervalMs) {}

  /**
   * Coordinator (Broker-0) configuration.
   *
   * @param brokerId the SWIM member ID of the coordinator broker (default "broker-0")
   * @param sessionTimeoutMs session timeout in milliseconds; consumers not sending a heartbeat
   *     within this window are considered dead (default 30000)
   * @param partitionCount number of coordinator Raft partitions the consumer-group coordinator is
   *     sharded across (default 1). Each group is owned by exactly one partition (by {@code
   *     groupId} hash); raise this to scale coordination across brokers. Must be identical on every
   *     node.
   */
  public record CoordinatorProperties(
      @DefaultValue("broker-0") String brokerId,
      @DefaultValue("30000") long sessionTimeoutMs,
      @DefaultValue("1") int partitionCount) {}

  /**
   * Consumer-side configuration.
   *
   * @param sessionTimeoutMs how long without a heartbeat before the coordinator marks a consumer
   *     dead and releases its partitions (default 10000)
   * @param rebalanceIntervalMs how often (in milliseconds) the coordinator loop runs to evict dead
   *     consumers, process ACK timeouts, drain the reassignment queue, and trigger rebalances
   *     (default 2000)
   * @param ackTimeoutMs maximum time (in milliseconds) a consumer has to ACK a revocation or
   *     assignment before the coordinator forces reassignment (default 5000)
   * @param maxInflightRevocations maximum number of concurrent in-flight revocations across all
   *     consumers in a group; new reassignments are deferred when this cap is reached (default 100)
   * @param heartbeatIntervalMs client-side sleep interval (in milliseconds) between consecutive
   *     heartbeat calls; the server enforces liveness via {@code sessionTimeoutMs} only (default
   *     1000)
   * @param partitionCount number of partitions for this consumer group; set at group creation time
   *     and immutable for the lifetime of the group (default 1 for backward compatibility — set
   *     explicitly in production deployments)
   */
  public record ConsumerProperties(
      @DefaultValue("10000") long sessionTimeoutMs,
      @DefaultValue("2000") long rebalanceIntervalMs,
      @DefaultValue("5000") long ackTimeoutMs,
      @DefaultValue("100") int maxInflightRevocations,
      @DefaultValue("1000") long heartbeatIntervalMs,
      @DefaultValue("1") int partitionCount) {}

  /**
   * Publish endpoint configuration.
   *
   * @param maxBatchSize maximum number of events per publish request (default 1000; valid range [1,
   *     10000])
   * @param maxEventBytes maximum decoded byte size for a single event payload (default 1 MiB)
   * @param maxBatchBytes maximum total encoded byte size for a publish request body (default 10
   *     MiB)
   */
  public record PublishProperties(
      @DefaultValue("1000") int maxBatchSize,
      @DefaultValue("1048576") int maxEventBytes,
      @DefaultValue("10485760") int maxBatchBytes) {}

  /**
   * Log retention configuration. Each data-partition leader keeps the most recent records and
   * compacts older log segments on a timer, independent of consumer progress. A consumer that falls
   * behind the retained window resumes via its {@code OffsetResetPolicy}.
   *
   * @param maxRecordsPerPartition maximum number of most-recent records to retain per partition;
   *     older log segments are eligible for compaction (default 1000000)
   * @param compactionIntervalMs how often (in milliseconds) a data-partition leader re-evaluates
   *     and applies retention compaction (default 60000)
   * @param segmentSizeBytes Raft log segment size; compaction is segment-granular, so this is the
   *     unit by which the retained window is rounded (default 32 MiB)
   */
  public record RetentionProperties(
      @DefaultValue("1000000") long maxRecordsPerPartition,
      @DefaultValue("60000") long compactionIntervalMs,
      @DefaultValue("33554432") long segmentSizeBytes) {}

  /**
   * RAFT consensus configuration.
   *
   * @param replicationFactor number of replicas per partition (default 1; set to 3+ for production)
   */
  public record RaftProperties(@DefaultValue("1") int replicationFactor) {}

  /**
   * SWIM/Netty cluster networking configuration.
   *
   * @param name Atomix cluster name; all nodes in the same cluster must use the same value (default
   *     "event-bridge")
   * @param nodeId SWIM member ID for this node; must be unique within the cluster (default
   *     "broker-0"). In single-node standalone mode this also equals {@code coordinator.broker-id}.
   * @param bindHost interface to bind the Netty internal transport to (default "0.0.0.0")
   * @param bindPort port to bind the Netty internal transport to (default 26502)
   * @param advertisedHost host advertised to other cluster members; defaults to {@code bindHost}
   *     unless {@code bindHost} is "0.0.0.0", in which case it defaults to "localhost"
   * @param advertisedPort port advertised to other cluster members; defaults to {@code bindPort}
   * @param initialContactPoints list of {@code host:port} addresses of existing cluster members to
   *     contact for bootstrapping; empty for single-node mode
   */
  public record ClusterProperties(
      @DefaultValue("event-bridge") String name,
      @DefaultValue("broker-0") String nodeId,
      @DefaultValue("0.0.0.0") String bindHost,
      @DefaultValue("26502") int bindPort,
      @DefaultValue("1") int clusterSize,
      String advertisedHost,
      Integer advertisedPort,
      @DefaultValue("26501") int commandApiPort,
      List<String> initialContactPoints) {

    public ClusterProperties {
      if (initialContactPoints == null) {
        initialContactPoints = List.of();
      }
    }

    /** Returns the effective advertised host (resolves "0.0.0.0" to "localhost"). */
    public String effectiveAdvertisedHost() {
      if (advertisedHost != null) {
        return advertisedHost;
      }
      return "0.0.0.0".equals(bindHost) ? "localhost" : bindHost;
    }

    /** Returns the effective advertised port (defaults to {@code bindPort}). */
    public int effectiveAdvertisedPort() {
      return advertisedPort != null ? advertisedPort : bindPort;
    }
  }
}
