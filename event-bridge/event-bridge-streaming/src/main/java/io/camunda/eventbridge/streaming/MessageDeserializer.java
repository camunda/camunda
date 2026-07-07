/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * Decodes a raw EventBridge payload into the record type a {@link Task} consumes. The source
 * partition and offset are provided for decoders that need to embed the source coordinate.
 *
 * <p>To skip records a consumer does not need, use a {@link RecordFilter} rather than this decoder
 * — the runtime applies the filter first, so filtered records are never decoded here.
 *
 * @param <R> the decoded record type
 */
@FunctionalInterface
public interface MessageDeserializer<R> {

  R deserialize(byte[] payload, int partition, long offset);
}
