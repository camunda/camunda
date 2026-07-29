/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

/**
 * The answer to a cancellation.
 *
 * <p>Cancelling is always accepted, so {@code wasRunning} is how an operator tells a cancellation
 * that stopped a rebalance apart from one that found nothing to stop.
 */
public record CancelRebalanceResponse(boolean wasRunning) {}
