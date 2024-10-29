/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import java.time.Instant;

/**
 * A health issue contains information about the cause for unhealthy/dead components. It can either
 * be a string message, a {@link Throwable} or another {@link HealthReport}.
 */
public record HealthIssue(String message, Throwable throwable, HealthReport cause, Instant since) {

  public static HealthIssue of(final String message, final Instant since) {
    return new HealthIssue(message, null, null, since);
  }

  public static HealthIssue of(final Throwable throwable, final Instant since) {
    return new HealthIssue(null, throwable, null, since);
  }

  public static HealthIssue of(final HealthReport cause, final Instant since) {
    return new HealthIssue(null, null, cause, since);
  }
}
