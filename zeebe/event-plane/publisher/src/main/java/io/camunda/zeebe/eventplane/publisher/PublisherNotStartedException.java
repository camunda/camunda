/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.segment.Identity;

/** A publisher that was asked to publish before it was started. */
public final class PublisherNotStartedException extends PublisherStateException {

  PublisherNotStartedException(final Identity log) {
    super("Expected the publisher of %s to be started, but it isn't".formatted(log), log);
  }
}
