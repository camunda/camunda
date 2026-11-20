/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * The connection to Hub of one physical tenant. The {@link HubUpdateReceiver} calls {@link #poll}
 * only while no policy update is open, so the client never has to pause on its own.
 *
 * <p>Until the Hub change set model is available, the client returns each update as the single
 * chunk of a policy update, with the Hub sequence as its {@code seqNo}.
 */
@NullMarked
public interface HubClient extends AutoCloseable {

  /**
   * Long-polls Hub for the next policy update after the given sequence.
   *
   * @param afterSeqNo the Hub sequence of the last committed policy update, or 0 if there is none
   * @return the next update, or empty if Hub has none before the poll times out
   */
  CompletableFuture<Optional<PolicyUpdateRecord>> poll(long afterSeqNo);

  @Override
  void close();
}
