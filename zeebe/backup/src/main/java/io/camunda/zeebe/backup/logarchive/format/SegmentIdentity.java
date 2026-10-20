/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

/**
 * Who wrote a segment: stored in the metadata of every segment (key 1).
 *
 * @param clusterId the cluster id as the event plane announcer resolves it
 * @param physicalTenantId the physical tenant (partition group) of the partition
 * @param partitionId the partition number
 */
public record SegmentIdentity(String clusterId, String physicalTenantId, int partitionId) {}
