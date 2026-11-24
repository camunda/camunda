/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * The connection to Hub of one physical tenant, through which the engine of partition 1 receives
 * policy updates. The {@link HubPolicyUpdateScheduler} calls it from its own actor, and never waits
 * for the returned future, so an implementation may long-poll Hub.
 *
 * <p>Until the Hub change set model and a splitter exist, the client returns an update already cut
 * into the chunks the engine buffers, all with the update's identifiers and its Hub sequence as
 * {@code seqNo}.
 */
@NullMarked
public interface HubPolicyClient {

  /** A client for a physical tenant that is not connected to Hub; the engine never polls it. */
  HubPolicyClient DISABLED = afterSeqNo -> CompletableFuture.completedFuture(List.of());

  /**
   * Polls Hub for the next policy update after the given sequence.
   *
   * @param afterSeqNo the Hub sequence of the committed policy, or 0 if none is committed
   * @return the chunks of the next update in index order, or an empty list if Hub has none
   */
  CompletableFuture<List<PolicyUpdateRecord>> poll(long afterSeqNo);
}
