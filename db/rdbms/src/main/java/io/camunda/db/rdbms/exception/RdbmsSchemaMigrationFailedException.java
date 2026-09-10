/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

/**
 * Thrown when a tenant's Liquibase migration fails for a reason re-running it cannot change: an
 * edited changeset whose checksum no longer matches {@code DATABASECHANGELOG}, or an unparseable
 * changelog. Lets the schema-initialization retry loop classify the failure terminal without
 * needing Liquibase on its own classpath.
 */
public class RdbmsSchemaMigrationFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RdbmsSchemaMigrationFailedException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
