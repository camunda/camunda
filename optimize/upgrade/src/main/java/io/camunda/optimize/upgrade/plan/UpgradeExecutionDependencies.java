/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;

public record UpgradeExecutionDependencies(
    DatabaseType databaseType,
    ConfigurationService configurationService,
    OptimizeIndexNameService indexNameService,
    DatabaseClient databaseClient,
    ObjectMapper objectMapper,
    DatabaseMetadataService<?> metadataService) {}
