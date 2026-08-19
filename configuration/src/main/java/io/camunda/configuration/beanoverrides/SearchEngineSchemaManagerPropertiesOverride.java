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

public class SearchEngineSchemaManagerPropertiesOverride {

  public static void applyTo(
      final Camunda camunda, final SearchEngineSchemaManagerProperties override) {
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
  }
}
