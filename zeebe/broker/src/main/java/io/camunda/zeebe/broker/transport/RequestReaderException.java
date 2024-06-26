/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport;

public abstract class RequestReaderException extends RuntimeException {

  public static final class InvalidTemplateException extends RequestReaderException {
    final int expectedTemplate;
    final int actualTemplate;

    public InvalidTemplateException(final int expectedTemplate, final int actualTemplate) {
      this.expectedTemplate = expectedTemplate;
      this.actualTemplate = actualTemplate;
    }
  }
}
