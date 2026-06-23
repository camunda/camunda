/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.topic;

/**
 * The naming convention for per-topic Raft groups. Each topic is its own group {@code
 * event-bridge-topic-<name>}; this name is both the Raft group/tenant and the gateway routing group
 * (the per-topic {@code BrokerInfo} is tagged with it), so the broker that provisions a topic and
 * the gateway that routes publish/fetch to it must agree on the string — hence it lives in core.
 */
public final class TopicGroups {

  /** Prefix for per-topic Raft groups. */
  public static final String TOPIC_GROUP_PREFIX = "event-bridge-topic-";

  private TopicGroups() {}

  /** The Raft group / routing group name hosting a topic's partitions. */
  public static String name(final String topic) {
    return TOPIC_GROUP_PREFIX + topic;
  }
}
