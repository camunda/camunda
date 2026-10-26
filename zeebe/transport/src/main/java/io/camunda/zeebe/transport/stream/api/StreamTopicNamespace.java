/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import java.util.Objects;

/**
 * Separates the topics of one kind of stream from another's, so that two stream services (e.g. job
 * streams and probe streams) can run side by side on the same nodes. The topics of a stream service
 * are otherwise fixed per physical tenant, and the push topic per node.
 *
 * <p>The {@link #DEFAULT} namespace is the one job streams have always used: its topics carry no
 * prefix, and it alone keeps the legacy (pre physical tenant) topics for rolling upgrades.
 */
public record StreamTopicNamespace(String prefix) {

  public static final StreamTopicNamespace DEFAULT = new StreamTopicNamespace("");

  public StreamTopicNamespace {
    Objects.requireNonNull(prefix, "prefix must not be null");
  }

  public static StreamTopicNamespace of(final String prefix) {
    return new StreamTopicNamespace(prefix);
  }

  /** The physical tenant scoped topic of this namespace. */
  public String topic(final StreamTopics topic, final String physicalTenantId) {
    return prefixed(topic.topic(physicalTenantId));
  }

  /** The topic on which a node receives the pushes of this namespace. */
  public String pushTopic() {
    return prefixed(StreamTopics.PUSH.legacyTopic());
  }

  /** Whether the legacy topics are served too; only the default namespace ever had them. */
  public boolean hasLegacyTopics() {
    return prefix.isEmpty();
  }

  private String prefixed(final String topic) {
    return prefix.isEmpty() ? topic : prefix + "-" + topic;
  }
}
