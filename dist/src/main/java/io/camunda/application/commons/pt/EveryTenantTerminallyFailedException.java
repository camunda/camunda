/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Thrown out of {@link PerTenantSchemaInitialization#awaitGate()} when every physical tenant's
 * schema initialization failed with a cause that retrying cannot repair, so the node has nothing
 * left to serve and no task that could ever change that.
 *
 * <p>Unchecked so that it travels the path an aborting startup already takes — out of the bean's
 * {@code afterPropertiesSet}, through the context refresh, to a non-zero exit.
 */
@NullMarked
public final class EveryTenantTerminallyFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private EveryTenantTerminallyFailedException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * @param failuresByTenant every physical tenant's terminal failure, keyed by tenant id; the first
   *     becomes the cause and the rest are suppressed, so that one stack trace carries all of them
   */
  static EveryTenantTerminallyFailedException of(final Map<String, Throwable> failuresByTenant) {
    final var causes = failuresByTenant.values().iterator();
    final var exception =
        new EveryTenantTerminallyFailedException(
            "Schema initialization failed with a cause that retrying cannot repair for every"
                + " physical tenant "
                + failuresByTenant.keySet()
                + ", so this node can serve none of them and is shutting down instead of starting"
                + " unable to serve. Fix the reported causes and restart. Each tenant's failure is"
                + " logged above; the first is attached as the cause and the rest as suppressed.",
            causes.next());
    causes.forEachRemaining(exception::addSuppressed);
    return exception;
  }
}
