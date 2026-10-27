/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

public final class DecodingFailed extends RuntimeException {

  public DecodingFailed(final Throwable cause) {
    super(cause);
  }

  public DecodingFailed(final String message) {
    super(message);
  }

  public DecodingFailed(final String message, final Exception e) {
    super(message, e);
  }
}
