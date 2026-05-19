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
 * Top-level configuration for the {@link Partitioning.Scheme#REGION_AWARE} partitioning scheme.
 *
 * <p>Lists all regions and their per-region configuration. Each entry carries its own {@link
 * Region#name()} so individual regions can be overridden via indexed environment variables (e.g.
 * {@code CAMUNDA_CLUSTER_PARTITIONING_ZONEAWARE_REGIONS_0_NAME}).
 *
 * <p>Example YAML configuration:
 *
 * <pre>{@code
 * camunda:
 *   cluster:
 *     size: 5
 *     replication-factor: 5
 *     zone: us-east1         # set per broker; env: CAMUNDA_CLUSTER_ZONE
 *     partitioning:
 *       scheme: REGION_AWARE
 *       zone-aware:
 *         regions:
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
public record ZoneAware(List<Region> regions) {}
