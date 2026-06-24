/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A broker's report that it has provisioned (started) some of a topic's partitions, sent back to
 * the metadata-group leader so it can advance the topic {@code CREATING -> ACTIVE} once every
 * partition is covered. The registry itself flows the other way by replication (each broker
 * observes the metadata Raft group); this small completion signal still travels over cluster
 * messaging because passive observers cannot write to the metadata log.
 */
public final class TopicProvisionedGossip {

  /** Cluster-communication subject brokers report provisioned partitions on. */
  public static final String SUBJECT = "event-bridge-topic-provisioned";

  private TopicProvisionedGossip() {}

  /** A broker's report: {@code topic} and the partition ids it has provisioned locally. */
  public record Report(String topic, List<Integer> partitions) {}

  /** Encodes a report as {@code topic;pid1,pid2,...}. */
  public static byte[] encode(final String topic, final List<Integer> partitions) {
    final var ids = new StringBuilder();
    for (var i = 0; i < partitions.size(); i++) {
      if (i > 0) {
        ids.append(',');
      }
      ids.append(partitions.get(i));
    }
    return (topic + ";" + ids).getBytes(StandardCharsets.UTF_8);
  }

  public static Report decode(final byte[] payload) {
    final var text = new String(payload, StandardCharsets.UTF_8);
    final var sep = text.indexOf(';');
    final var topic = text.substring(0, sep);
    final List<Integer> partitions = new ArrayList<>();
    final var ids = text.substring(sep + 1);
    if (!ids.isBlank()) {
      for (final var id : ids.split(",")) {
        partitions.add(Integer.parseInt(id));
      }
    }
    return new Report(topic, partitions);
  }

  /** Outbound sink: a broker publishes its provisioned partitions for a topic. */
  @FunctionalInterface
  public interface Publisher {
    void publish(String topic, List<Integer> partitions);
  }
}
