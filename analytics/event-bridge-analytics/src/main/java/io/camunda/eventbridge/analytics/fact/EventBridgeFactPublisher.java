/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

import io.camunda.eventbridge.client.EventBridgeClient;

/**
 * A {@link FactSink} that publishes facts to an Event Bridge fact topic, keyed and routed by {@code
 * processDefinitionKey} so all facts of one definition land on the same partition — the partition
 * whose leader runs the matching aggregator. {@code publish} blocks until the broker acks, giving
 * the publish-then-advance (depth-1) delivery the MVP relies on.
 */
public final class EventBridgeFactPublisher implements FactSink {

  private final EventBridgeClient client;
  private final String factTopic;
  private final int partitionCount;
  private final ProcessInstanceExecutionTimeFactCodec codec =
      new ProcessInstanceExecutionTimeFactCodec();

  public EventBridgeFactPublisher(
      final EventBridgeClient client, final String factTopic, final int partitionCount) {
    this.client = client;
    this.factTopic = factTopic;
    this.partitionCount = partitionCount;
  }

  @Override
  public void publish(final ProcessInstanceExecutionTimeFact fact) {
    final int partition = partitionFor(fact.processDefinitionKey(), partitionCount);
    final String key = Long.toString(fact.processDefinitionKey());
    client.publishToTopic(factTopic, partition, key, codec.serialize(fact)).join();
  }

  /** Routes a definition key to a 1-based topic partition (partitions are numbered from 1). */
  public static int partitionFor(final long processDefinitionKey, final int partitionCount) {
    return 1 + Math.floorMod(Long.hashCode(processDefinitionKey), partitionCount);
  }
}
