/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carries the topic registry as a structured msgpack array of {@link TopicEntry} (name, counts,
 * status, and committed placement) — no hand-rolled string payload; the gateway maps the entries
 * straight to its DTOs.
 */
public class ListTopicsResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final ArrayProperty<TopicEntry> topicsProp =
      new ArrayProperty<>("topics", TopicEntry::new);

  public ListTopicsResponse() {
    super(2);
    declareProperty(errorCodeProp).declareProperty(topicsProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public ListTopicsResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public ListTopicsResponse addTopic(
      final String name,
      final int partitionCount,
      final int replicationFactor,
      final String status,
      final Map<Integer, List<Integer>> assignment) {
    topicsProp.add().set(name, partitionCount, replicationFactor, status, assignment);
    return this;
  }

  /** The registered topics, decoded into immutable views. */
  public List<Topic> getTopics() {
    final List<Topic> topics = new ArrayList<>();
    topicsProp.forEach(
        entry ->
            topics.add(
                new Topic(
                    entry.getName(),
                    entry.getPartitionCount(),
                    entry.getReplicationFactor(),
                    entry.getStatus(),
                    entry.getAssignment())));
    return topics;
  }

  /** A decoded topic: its config and committed placement ({@code partition → replica node ids}). */
  public record Topic(
      String name,
      int partitionCount,
      int replicationFactor,
      String status,
      Map<Integer, List<Integer>> assignment) {}
}
