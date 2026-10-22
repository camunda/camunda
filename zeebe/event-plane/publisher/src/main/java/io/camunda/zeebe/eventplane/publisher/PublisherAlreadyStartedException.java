/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.segment.LogId;

/** A publisher that was started a second time. */
public final class PublisherAlreadyStartedException extends PublisherStateException {

  PublisherAlreadyStartedException(final LogId log) {
    super("Expected the publisher of %s not to be started, but it is".formatted(log), log);
  }
}
