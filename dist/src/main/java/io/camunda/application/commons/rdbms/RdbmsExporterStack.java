/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;

/**
 * Holds all the components that make up an independent RDBMS exporter stack. This includes the
 * service layer, schema manager, and vendor-specific database properties.
 */
public record RdbmsExporterStack(
    RdbmsService rdbmsService,
    RdbmsSchemaManager rdbmsSchemaManager,
    VendorDatabaseProperties vendorDatabaseProperties) {}
