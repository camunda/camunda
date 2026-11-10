/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import org.jspecify.annotations.Nullable;

/**
 * What a node knows about the leader of a partition.
 *
 * @param memberId the id of the broker that leads the partition
 * @param zone the zone, which is the region, in the broker id. Null if the broker has no zone.
 */
public record LeaderInfo(String memberId, @Nullable String zone) {}
