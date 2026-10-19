/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import java.util.List;

/**
 * Creates partition listeners for a single physical tenant. Unlike the global listeners passed to
 * {@link Broker}, the returned listeners are only registered with that tenant's partition group, so
 * the bare partition id they receive is unambiguous.
 */
@FunctionalInterface
public interface PhysicalTenantPartitionListenerFactory {

  PhysicalTenantPartitionListenerFactory NONE = physicalTenantId -> List.of();

  List<PartitionListener> create(String physicalTenantId);
}
