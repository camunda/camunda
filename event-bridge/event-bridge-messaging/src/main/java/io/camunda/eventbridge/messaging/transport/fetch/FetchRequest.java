/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.fetch;

/**
 * Internal fetch request handed to the {@link
 * io.camunda.eventbridge.messaging.fetch.EventStreamFetcher}. Decoded from the SBE {@code
 * FetchRequest} by {@link FetchRequestHandler}.
 *
 * @param consumerId identifies the consumer for pending fetch management
 * @param partitionId which partition to read from
 * @param offset position to start reading from (inclusive)
 * @param maxBytes maximum bytes to return in the response
 * @param minBytes minimum bytes before responding (long-poll threshold)
 * @param maxWaitMs maximum time to wait for minBytes (0 = respond immediately)
 */
public record FetchRequest(
    String consumerId, int partitionId, long offset, int maxBytes, int minBytes, long maxWaitMs) {}
