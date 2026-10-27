/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

/** Constants shared between the job processors and the event appliers that replay their events. */
public final class JobErrorMarkers {

  /**
   * Marker element ID. This ID is used to indicate that a given catch event could not be found. The
   * marker ID is used to prevent repeated catch event lookups, which is an expensive operation
   * (particularly when no catch event can be found)
   */
  public static final String NO_CATCH_EVENT_FOUND = "NO_CATCH_EVENT_FOUND";

  private JobErrorMarkers() {}
}
