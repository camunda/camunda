/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beans;

import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;

public class SearchEngineSchemaManagerProperties extends SchemaManagerConfiguration {

  @VisibleForTesting
  public static final String CREATE_SCHEMA_PROPERTY =
      "camunda.database.schema-manager.createSchema";

  @VisibleForTesting
  public static final String CREATE_SCHEMA_ENV_VAR =
      "CAMUNDA_DATABASE_SCHEMA_MANAGER_CREATE_SCHEMA";
}
