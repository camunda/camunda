/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/**
 * Response to a rebalance cancellation request.
 *
 * <p>Cancelling is always accepted, so {@code wasRunning} distinguishes a cancellation that stopped
 * a running rebalance from a no-op.
 */
public record CancelRebalanceResponse(boolean wasRunning) {}
