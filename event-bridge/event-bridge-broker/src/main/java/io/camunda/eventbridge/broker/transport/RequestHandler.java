/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport;

import java.util.concurrent.CompletableFuture;

/** Handles a request for a specific partition topic. */
@FunctionalInterface
public interface RequestHandler {

  CompletableFuture<byte[]> handle(byte[] requestBytes);
}
