/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

/**
 * A topic's retention policy (event-bridge ADR 0001, decision 2).
 *
 * <p>{@code DELETE} is today's behavior — a retention-bounded log where the oldest records are
 * discarded — and is the default everywhere a topic is created or decoded without specifying a
 * policy, so existing topics and API calls are unaffected. {@code COMPACT} keeps only the latest
 * record per key (a keyed batch, see {@code EventBridgeBatch#KEYED_MASK}); publishing an unkeyed
 * batch to a {@code COMPACT} topic is rejected.
 */
public enum CleanupPolicy {
  DELETE,
  COMPACT
}
