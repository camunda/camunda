/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

import io.atomix.cluster.MemberId;
import java.util.Optional;

/**
 * Notified of changes to a {@link MemberKey}.
 *
 * @param <T> the type of the value
 */
@FunctionalInterface
public interface MemberListener<T> {

  /**
   * Called when a member's copy changes.
   *
   * @param value the member's current copy; empty when the member left the cluster or restarted, or
   *     when its value can no longer be decoded locally
   */
  void onChange(MemberId member, Optional<T> value);
}
