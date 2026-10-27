/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;

/** An operation that changes the configuration. */
public sealed interface ClusterConfigurationChangeOperation
    permits PartitionGroupOperation, GlobalChangeOperation {

  MemberId memberId();

  /**
   * The id of the broker that applies this operation, including its zone if it belongs to one.
   * Offered so that callers which report operations outside this module — the REST layer, which
   * does not depend on {@link MemberId} — do not have to derive the id themselves.
   */
  default String brokerId() {
    return memberId().id();
  }
}
