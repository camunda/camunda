/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal;

import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.Partitioner;

/**
 * Immutable client configuration assembled by {@link EventBridgeClientImpl.BuilderImpl} and passed
 * to the {@link EventBridgeClientImpl} and its consumer collaborators. Surfaces the values that
 * were previously hardcoded constants in the consumer internals (long-poll duration, fetch sizing,
 * prefetch depth) plus the scheduler sizing and partitioning strategy.
 *
 * @param gatewayUrl base URL of the gateway (no trailing slash)
 * @param offsetResetPolicy start policy for newly assigned partitions with no committed offset
 * @param schedulerThreads size of the scheduled executor used for heartbeat cadence and prefetch
 *     backoff (I/O itself runs async on the JDK HTTP client's executor)
 * @param longPollMs how long a background fetch parks on the broker before returning empty
 * @param fetchMaxBytes max bytes requested per (topic, partition) fetch
 * @param fetchMinBytes min committed bytes the broker waits to accumulate before responding
 * @param prefetchDepth max prefetch depth per partition (buffered batches plus in-flight fetches)
 * @param maxBufferedBytes max total payload bytes buffered across all partitions before the
 *     consumer applies fetch backpressure
 * @param maxPublishBytes max total bytes of in-flight publish batches before publishing applies
 *     backpressure
 * @param heartbeatIntervalMs interval between scheduled heartbeats to the coordinator
 * @param partitioner strategy routing a keyed record to a partition for {@code publishToTopic}
 */
public record ClientConfig(
    String gatewayUrl,
    OffsetResetPolicy offsetResetPolicy,
    int schedulerThreads,
    long longPollMs,
    int fetchMaxBytes,
    int fetchMinBytes,
    int prefetchDepth,
    long maxBufferedBytes,
    long maxPublishBytes,
    long heartbeatIntervalMs,
    Partitioner partitioner) {}
