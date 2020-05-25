/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.backpressure;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import java.util.Optional;

@FunctionalInterface
public interface PartitionAwareRequestLimiter {
  Optional<Listener> acquire(int partitionId, BrokerRequest<?> request);
}
