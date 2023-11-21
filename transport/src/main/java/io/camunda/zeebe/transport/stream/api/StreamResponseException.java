/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.util.exception.UnrecoverableException;

/** An exception returned */
public class StreamResponseException extends UnrecoverableException {

  public StreamResponseException(final ErrorResponse response) {
    super(
        "Remote stream server error: [code=%s, message='%s']"
            .formatted(response.code(), response.message()));
  }
}
