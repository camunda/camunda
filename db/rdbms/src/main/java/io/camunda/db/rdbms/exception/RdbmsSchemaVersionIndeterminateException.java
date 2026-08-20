/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

/**
 * Thrown when a physical tenant's RDBMS schema version cannot be determined at all: the data source
 * or the application version is absent, or the value already stored in {@code RDBMS_SCHEMA_VERSION}
 * is not a semantic version.
 *
 * <p>Distinct from {@link RdbmsSchemaVersionUnreadableException} because the two need opposite
 * treatment from the schema-initialization retry loop. Nothing here is repaired by trying again — a
 * wiring defect and an invalid stored value both need an operator — so this is classified terminal,
 * which stops that tenant retrying and logs once instead of a stack trace every backoff interval.
 *
 * <p>Extends {@link IllegalStateException}, which every one of these sites threw before the split,
 * so that callers and tests keyed on that type are unaffected.
 */
public class RdbmsSchemaVersionIndeterminateException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public RdbmsSchemaVersionIndeterminateException(final String message) {
    super(message);
  }
}
