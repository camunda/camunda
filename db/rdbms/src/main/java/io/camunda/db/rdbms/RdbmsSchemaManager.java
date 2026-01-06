/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

/**
 * Interface for managing RDBMS database schemas.
 *
 * <p>This interface defines methods for checking the initialization status of the database schema.
 */
public interface RdbmsSchemaManager {

  boolean isInitialized();
}
