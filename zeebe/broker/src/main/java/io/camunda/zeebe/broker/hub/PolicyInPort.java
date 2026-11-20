/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/** Hands a policy update from Hub to the engine of a physical tenant, and asks for its status. */
@NullMarked
public interface PolicyInPort {

  /**
   * Sends the update to partition 1.
   *
   * @return completes once partition 1 has buffered every chunk of the update; fails if partition 1
   *     rejected a chunk or could not be reached
   */
  CompletableFuture<Void> apply(PolicyUpdateRecord update);

  /**
   * Asks partition 1 for the committed policy and the open update. The engine answers in log order,
   * so the status reflects every command written before.
   */
  CompletableFuture<PolicyStatus> queryStatus();
}
