/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

/**
 * A no-operation implementation of the {@link RdbmsSchemaManager} interface.
 *
 * <p>This class always indicates that the schema is initialized.
 */
public class NoopSchemaManager implements RdbmsSchemaManager {

  @Override
  public boolean isInitialized() {
    return true;
  }
}
