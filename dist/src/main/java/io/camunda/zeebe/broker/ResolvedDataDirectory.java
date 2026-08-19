/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

/**
 * The broker's primary data directory, after any {@link
 * io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider} adjustment (e.g. the node-id segment
 * appended for a dynamic node id provider) has been applied.
 *
 * <p>This is the single source of truth every physical tenant's {@code BrokerCfg} must use as its
 * data directory, so that all tenants keep sharing the same node-level data directory root instead
 * of each independently re-deriving it from their own raw configuration.
 */
public record ResolvedDataDirectory(String directory) {}
