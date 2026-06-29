/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.topic;

/**
 * A fully-resolved topic to provision on startup: the configured {@code event-bridge.topics} entry
 * with its partition count and replication factor already defaulted from the broker/raft config.
 * The metadata leader creates each of these once it is ready (idempotently — an already-registered
 * topic is left untouched), so they survive restarts via the replicated topic registry.
 */
public record AutoCreatedTopic(String name, int partitionCount, int replicationFactor) {}
