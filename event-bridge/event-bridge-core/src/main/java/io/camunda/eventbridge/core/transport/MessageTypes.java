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

  /** Gateway → coordinator: consumer liveness signal. */
  public static final String HEARTBEAT_REQUEST = "eb.heartbeat.request";

  private MessageTypes() {}
}
