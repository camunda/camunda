/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.publish;

import io.atomix.cluster.messaging.InboundPayload;
import io.camunda.eventbridge.protocol.ExecutePublishRequestDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;

public record PublishRequest(InboundPayload payload, int batchOffset, int batchLength) {

  private static final int BATCH_OFFSET =
      MessageHeaderDecoder.ENCODED_LENGTH
          + ExecutePublishRequestDecoder.BLOCK_LENGTH
          + ExecutePublishRequestDecoder.entryBatchHeaderLength();

  static PublishRequest from(final InboundPayload payload) {
    if (payload.length() <= BATCH_OFFSET) {
      throw new IllegalArgumentException("Request too short");
    }

    return new PublishRequest(payload, BATCH_OFFSET, payload.length() - BATCH_OFFSET);
  }
}
