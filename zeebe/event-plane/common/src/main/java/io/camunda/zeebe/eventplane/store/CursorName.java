/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

/**
 * What the key of a consumer group's cursor says: the group, and the log, a partition of a physical
 * tenant of a cluster. See {@link ObjectNames#cursor}.
 *
 * @param group the consumer group
 * @param cluster the log's cluster
 * @param tenant the log's physical tenant
 * @param partition the log's partition
 */
public record CursorName(String group, String cluster, String tenant, int partition) {}
