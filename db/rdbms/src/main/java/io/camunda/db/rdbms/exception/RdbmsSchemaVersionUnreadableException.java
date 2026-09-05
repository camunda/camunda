/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

/**
 * Thrown when a physical tenant's RDBMS schema version could not be read or recorded — a refused
 * connection, a missing grant, a broken socket, a rolled-back write.
 *
 * <p>Distinct from {@link RdbmsSchemaVersionIndeterminateException} because the two need opposite
 * treatment from the schema-initialization retry loop. Every cause here can be repaired while the
 * node runs, so this is classified retryable and the tenant keeps trying.
 *
 * <p>Extends {@link IllegalStateException}, which this site threw before the split, so that callers
 * and tests keyed on that type are unaffected.
 */
public class RdbmsSchemaVersionUnreadableException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public RdbmsSchemaVersionUnreadableException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
