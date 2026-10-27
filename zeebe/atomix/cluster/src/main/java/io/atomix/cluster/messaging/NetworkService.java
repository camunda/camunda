/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

import org.jspecify.annotations.NullMarked;

/**
 * The set of transports a cluster node uses to talk to its peers, grouped behind a single owner.
 *
 * <p>A node communicates over two complementary primitives: {@link MessagingService} for reliable,
 * addressed messages (requests, replies, fire-and-forget sends), and {@link UnicastService} for
 * unreliable fire-and-forget messages.
 *
 * <p>Expected usage is for the caller to use the accessor (e.g. {@link #messagingService()})
 * depending on the messaging semantics required.
 */
@NullMarked
public interface NetworkService {

  /**
   * Returns the service for reliable, addressed messaging with peers.
   *
   * @return the messaging primitive
   */
  MessagingService messagingService();

  /**
   * Returns the service for unreliable, fire-and-forget messaging with peers.
   *
   * @return the unicast primitive
   */
  UnicastService unicastService();
}
