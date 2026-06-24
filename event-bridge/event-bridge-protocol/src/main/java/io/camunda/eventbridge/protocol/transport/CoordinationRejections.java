/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.transport;

import io.camunda.eventbridge.protocol.CoordinateRejectionType;
import io.camunda.zeebe.protocol.record.RejectionType;

/**
 * Translates between the platform {@link RejectionType} a coordinator processor stamps on a {@code
 * COMMAND_REJECTION} and the wire-level {@link CoordinateRejectionType} carried in the {@code
 * ExecuteCoordinateResponse} envelope. The broker encodes a rejection on the way out; the gateway
 * decodes it back into a {@code BrokerRejection} and maps it to an HTTP status.
 *
 * <p>Only the subset of {@link RejectionType} the coordinator actually emits has a dedicated wire
 * value; anything else collapses to {@link CoordinateRejectionType#PROCESSING_ERROR} (a 5xx at the
 * gateway) rather than being silently dropped.
 */
public final class CoordinationRejections {

  private CoordinationRejections() {}

  /** Maps the platform rejection type to its wire value for encoding the reply. */
  public static CoordinateRejectionType toWire(final RejectionType type) {
    return switch (type) {
      case INVALID_ARGUMENT -> CoordinateRejectionType.INVALID_ARGUMENT;
      case NOT_FOUND -> CoordinateRejectionType.NOT_FOUND;
      case INVALID_STATE -> CoordinateRejectionType.INVALID_STATE;
      case ALREADY_EXISTS -> CoordinateRejectionType.ALREADY_EXISTS;
      default -> CoordinateRejectionType.PROCESSING_ERROR;
    };
  }

  /**
   * Maps the decoded wire value back to a platform rejection type for the {@code BrokerRejection}.
   */
  public static RejectionType fromWire(final CoordinateRejectionType type) {
    return switch (type) {
      case INVALID_ARGUMENT -> RejectionType.INVALID_ARGUMENT;
      case NOT_FOUND -> RejectionType.NOT_FOUND;
      case INVALID_STATE -> RejectionType.INVALID_STATE;
      case ALREADY_EXISTS -> RejectionType.ALREADY_EXISTS;
      default -> RejectionType.PROCESSING_ERROR;
    };
  }
}
