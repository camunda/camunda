/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

public enum CoordinationErrorCode {
  NONE("none"),
  UNKNOWN_MEMBER_ID("unkown_member_id"),
  FENCED_MEMBER_EPOCH("fenced_member_epoch"),
  FENCED_MEMBER_ACTIVE("fenced_member_active"),
  INVALID_GROUP_ID("invalid_group_id"),
  // A static member (group.instance.id) tried to join while that instance id is still held by a
  // live member. The new joiner is fenced; it must wait for the incumbent to
  // leave/expire.
  UNRELEASED_INSTANCE_ID("unreleased_instance_id"),
  // The member is valid but does not own the partition it is trying to commit (stale assignment).
  NOT_PARTITION_OWNER("not_partition_owner"),

  REBALANCE_IN_PROGRESS("rebalance_in_progress"),

  // Topic management.
  INVALID_TOPIC("invalid_topic"),
  TOPIC_ALREADY_EXISTS("topic_already_exists"),
  TOPIC_NOT_FOUND("topic_not_found"),

  UNKNOWN("unknown");

  private final String id;

  CoordinationErrorCode(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
