/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.List;

/**
 * Top-level configuration for the {@link Partitioning.Scheme#ZONE_AWARE} partitioning scheme.
 *
 * <p>Lists all zones and their per-zone configuration. Each entry carries its own {@link
 * Zone#name()} so individual zones can be overridden via indexed environment variables (e.g. {@code
 * CAMUNDA_CLUSTER_PARTITIONING_ZONEAWARE_ZONES_0_NAME}).
 *
 * <p>Example YAML configuration:
 *
 * <pre>{@code
 * camunda:
 *   cluster:
 *     size: 5
 *     replication-factor: 5
 *     # set per broker; env: CAMUNDA_CLUSTER_ZONE
 *     zone: us-east1
 *     partitioning:
 *       scheme: REGION_AWARE
 *       zone-aware:
 *         zones:
 *           - name: us-east1
 *             numberOfBrokers: 2
 *             numberOfReplicas: 2
 *             priority: 1000
 *           - name: us-west1
 *             numberOfBrokers: 2
 *             numberOfReplicas: 2
 *             priority: 500
 *           - name: euro-east1
 *             numberOfBrokers: 1
 *             numberOfReplicas: 1
 *             priority: 10
 * }</pre>
 */
public record ZoneAware(List<Zone> zones) {}
