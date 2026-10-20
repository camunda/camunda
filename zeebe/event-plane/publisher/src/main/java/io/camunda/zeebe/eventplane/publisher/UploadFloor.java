/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import java.util.UUID;

/**
 * A partition's last uploaded entry, in the history it was uploaded for, and the position of the
 * last record uploaded, or -1 if none was. The journal may be compacted up to the position, and a
 * new leader resumes after the index.
 */
public record UploadFloor(UUID history, long index, long position) {

  public UploadFloor {
    if (index < 1) {
      throw new IllegalArgumentException(
          "Expected an upload floor at index 1 or later, but got " + index);
    }
    if (position < -1) {
      throw new IllegalArgumentException(
          "Expected an upload floor at position -1 or later, but got " + position);
    }
  }
}
