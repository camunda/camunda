/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

public enum ClusterConfigurationRequestTopics {
  ADD_MEMBER("topology-member-add"),
  REMOVE_MEMBER("topology-member-remove"),
  JOIN_PARTITION("topology-partition-join"),
  LEAVE_PARTITION("topology-partition-leave"),
  REASSIGN_PARTITIONS("topology-partition-reassign"),
  SCALE_MEMBERS("topology-member-scale"),
  QUERY_TOPOLOGY("topology-query"),
  CANCEL_CHANGE("topology-change-cancel"),
  FORCE_SCALE_DOWN("topology-force-scale-down"),
  DISABLE_EXPORTER("topology-exporter-disable"),
  ENABLE_EXPORTER("topology-exporter-enable"),
  DELETE_EXPORTER("topology-exporter-delete"),

  SCALE_CLUSTER("topology-cluster-scale"),
  PATCH_CLUSTER("topology-cluster-patch"),
  PURGE("topology-cluster-purge"),
  FORCE_REMOVE_BROKERS("topology-broker-force-remove"),
  UPDATE_ROUTING_STATE("topology-cluster-update-routing-state");

  private final String topic;

  ClusterConfigurationRequestTopics(final String topic) {
    this.topic = topic;
  }

  public String topic() {
    return topic;
  }
}
