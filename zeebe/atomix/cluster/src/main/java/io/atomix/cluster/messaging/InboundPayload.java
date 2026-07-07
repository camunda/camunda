/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

import org.agrona.DirectBuffer;

/**
 * An inbound message payload that can be read in place. Large payloads are backed by a retained
 * slice of the network receive buffer instead of a heap copy, so ownership is explicit: whoever
 * takes the payload from the dispatch path must call {@link #release()} exactly once, on every
 * path, after the bytes were consumed (heap-backed payloads make this a no-op).
 */
public interface InboundPayload extends ManagedPayload {

  /**
   * Zero-copy view over the payload bytes. Only valid until {@link #release()}; callers that need
   * the bytes afterwards must copy them out (e.g. via {@link #toBytes()}).
   */
  DirectBuffer view();
}
