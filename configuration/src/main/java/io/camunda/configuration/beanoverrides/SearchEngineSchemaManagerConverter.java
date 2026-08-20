/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;

/** Converts a {@link Camunda} configuration into a {@link SearchEngineSchemaManagerProperties}. */
public class SearchEngineSchemaManagerConverter {

  public static SearchEngineSchemaManagerProperties convert(final Camunda camunda) {
    final SearchEngineSchemaManagerProperties override = new SearchEngineSchemaManagerProperties();

    override.setVersionCheckRestrictionEnabled(
        camunda.getSystem().getUpgrade().getEnableVersionCheck());

    camunda
        .getData()
        .getSecondaryStorage()
        .elasticsearchOrOpensearch()
        .ifPresent(
            secondaryStorage -> {
              override.setPerformCleanup(secondaryStorage.isPerformCleanup());
              override.setCreateSchema(secondaryStorage.isCreateSchema());
            });

    return override;
  }
}
