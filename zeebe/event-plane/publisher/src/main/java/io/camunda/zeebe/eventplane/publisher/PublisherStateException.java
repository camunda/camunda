/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.segment.Identity;

/** A call to a publisher that its state doesn't allow: it isn't started yet, or it is already. */
public abstract sealed class PublisherStateException extends IllegalStateException
    permits PublisherNotStartedException, PublisherAlreadyStartedException {

  private final Identity log;

  PublisherStateException(final String message, final Identity log) {
    super(message);
    this.log = log;
  }

  /** The log whose publisher was called. */
  public Identity log() {
    return log;
  }
}
