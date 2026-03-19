/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.transport;

/**
 * Central registry of all Netty messaging service type strings used between the Event Bridge
 * gateway and brokers. Type constants must not be defined ad hoc in handler-registration code.
 *
 * <p>Naming convention: {@code "eb.<area>.<operation>"}
 */
public final class MessageTypes {

  /** Gateway → partition leader: publish a batch of raw events. */
  public static final String PRODUCE_REQUEST = "eb.produce.request";

  /** Gateway → partition leader: pull events (long-poll fetch). */
  public static final String FETCH_REQUEST = "eb.fetch.request";

  /** Gateway → coordinator: register a consumer and trigger rebalance. */
  public static final String SUBSCRIBE_REQUEST = "eb.subscribe.request";

  /** Coordinator → gateway: subscribe result (assigned partitions, generation). */
  public static final String SUBSCRIBE_RESPONSE = "eb.subscribe.response";

  /** Gateway → coordinator: consumer liveness signal. */
  public static final String HEARTBEAT_REQUEST = "eb.heartbeat.request";

  /** Gateway → coordinator: idempotent offset commit. */
  public static final String COMMIT_OFFSET_REQUEST = "eb.commit-offset.request";

  /** Gateway → coordinator: query current partition assignment for a consumer group. */
  public static final String FETCH_ASSIGNMENT_REQUEST = "eb.fetch-assignment.request";

  /** Coordinator → partition leader: truncate log up to a given position. */
  public static final String TRUNCATE_REQUEST = "eb.truncate.request";

  /** Partition leader → coordinator: truncate acknowledgement. */
  public static final String TRUNCATE_RESPONSE = "eb.truncate.request.response";

  /** Gateway → partition leader: query the highest committed log position. */
  public static final String LATEST_POSITION_REQUEST = "eb.latest-position.request";

  /** Partition leader → gateway: latest position result. */
  public static final String LATEST_POSITION_RESPONSE = "eb.latest-position.request.response";

  /** Gateway → coordinator: query current assignment without triggering rebalance. */
  public static final String FETCH_ASSIGNMENT_RESPONSE = "eb.fetch-assignment.request.response";

  private MessageTypes() {}
}
