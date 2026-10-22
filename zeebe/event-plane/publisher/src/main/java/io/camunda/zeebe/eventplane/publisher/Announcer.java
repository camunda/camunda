/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.segment.LogId;

/**
 * Tells the Event Plane's service about uploads. It never throws and never blocks the publisher:
 * workers also list the store, so a lost announcement only delays them.
 */
public interface Announcer extends AutoCloseable {

  /** Announces nothing. */
  Announcer NONE = (log, upload) -> {};

  void announce(LogId log, Upload upload);

  /** Gives announcements in flight a moment to finish. */
  @Override
  default void close() {}
}
