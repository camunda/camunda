/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.schema.SchemaStartup;

public class TestSchemaStartup extends SchemaStartup {

  @Override
  public void initializeSchema() throws MigrationException {
    // Workaround to avoid @PostConstruct logic in #initializeSchema()
    // on test initialization. Test cases will trigger
    // schema initialization manually via #initializeSchemaOnDemand
  }

  public void initializeSchemaOnDemand() throws MigrationException {
    super.initializeSchema();
  }
}
