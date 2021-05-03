/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.RecordMetadata;

public final class ProcessingException extends RuntimeException {

  public ProcessingException(
      final String message,
      final LoggedEvent event,
      final RecordMetadata metadata,
      final Throwable cause) {
    super(formatMessage(message, event, metadata), cause);
  }

  private static String formatMessage(
      final String message, final LoggedEvent event, final RecordMetadata metadata) {
    return String.format("%s [%s %s]", message, formatEvent(event), formatMetadata(metadata));
  }

  private static String formatEvent(final LoggedEvent event) {
    if (event == null) {
      return "LoggedEvent [null]";
    }
    return event.toString();
  }

  private static String formatMetadata(final RecordMetadata metadata) {
    if (metadata == null) {
      return "RecordMetadata{null}";
    }
    return metadata.toString();
  }
}
