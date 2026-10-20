/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import java.io.IOException;
import java.net.URI;

/** The service answered a request with another status than 200. */
public final class ServiceResponseException extends IOException {

  private final URI uri;
  private final int status;

  ServiceResponseException(final URI uri, final int status, final String body) {
    super("Expected status 200 for GET %s, but got %d: %s".formatted(uri, status, body));
    this.uri = uri;
    this.status = status;
  }

  /** The URI that was requested. */
  public URI uri() {
    return uri;
  }

  /** The status of the response. */
  public int status() {
    return status;
  }
}
