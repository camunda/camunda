/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

import io.atomix.utils.Managed;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link NetworkService} that also owns the lifecycle of the transports behind it.
 *
 * <p>This is the single point at which a node's peer-to-peer transports are started and stopped.
 */
@NullMarked
public interface ManagedNetworkService extends NetworkService, Managed<NetworkService> {}
