/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

/**
 * Where a consumer starts reading a newly assigned partition that has <em>no</em> committed offset.
 * When a committed offset exists, the consumer always resumes from it regardless of this policy.
 */
public enum OffsetResetPolicy {
  /** Start from the oldest retained record (replay everything available). */
  EARLIEST,
  /** Start from the current end of the log (consume only records produced from now on). */
  LATEST
}
