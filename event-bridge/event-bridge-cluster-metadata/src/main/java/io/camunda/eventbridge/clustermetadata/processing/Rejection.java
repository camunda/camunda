/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;

/**
 * Why a topic command was rejected — the event-bridge counterpart of the engine's {@code
 * Rejection}. Carries the domain {@link CoordinationErrorCode} (echoed to the client in the
 * protocol response) and a reason; {@link #rejectionType()} maps the code to the platform {@link
 * RejectionType} stamped on the replicated {@code COMMAND_REJECTION} record.
 */
public record Rejection(CoordinationErrorCode code, String reason) {

  public RejectionType rejectionType() {
    return switch (code) {
      case INVALID_TOPIC, INVALID_GROUP_ID -> RejectionType.INVALID_ARGUMENT;
      case TOPIC_NOT_FOUND, UNKNOWN_MEMBER_ID -> RejectionType.NOT_FOUND;
      case TOPIC_ALREADY_EXISTS,
          FENCED_MEMBER_EPOCH,
          FENCED_MEMBER_ACTIVE,
          NOT_PARTITION_OWNER,
          REBALANCE_IN_PROGRESS ->
          RejectionType.INVALID_STATE;
      default -> RejectionType.PROCESSING_ERROR;
    };
  }
}
