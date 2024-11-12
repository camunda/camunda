/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.ArrayList;
import java.util.List;

/** An exception returned */
public class StreamResponseException extends UnrecoverableException {

  private final ErrorCode code;
  private final String message;
  private final List<ErrorDetail> details;

  public StreamResponseException(final ErrorResponse response) {
    super(
        "Remote stream server error: [code=%s, message='%s', details=%s]"
            .formatted(response.code(), response.message(), response.details()));

    code = response.code();
    message = response.message();
    details = new ArrayList<>(response.details());
  }

  public ErrorCode code() {
    return code;
  }

  public String message() {
    return message;
  }

  public List<ErrorDetail> details() {
    return details;
  }

  public interface ErrorDetail {
    ErrorCode code();

    String message();
  }
}
