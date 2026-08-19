/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.Retention;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;

public class SearchEngineRetentionPropertiesOverride {

  public static void applyTo(
      final Camunda camunda, final SearchEngineRetentionProperties override) {
    populateFromRetention(camunda, override);
    populateFromSecondaryStorage(camunda, override);
  }

  private static void populateFromRetention(
      final Camunda camunda, final SearchEngineRetentionProperties override) {
    final Retention retention = camunda.getData().getSecondaryStorage().getRetention();
    override.setEnabled(retention.isEnabled());
    override.setMinimumAge(retention.getMinimumAge());
  }

  private static void populateFromSecondaryStorage(
      final Camunda camunda, final SearchEngineRetentionProperties override) {
    final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();

    final DocumentBasedSecondaryStorageDatabase database =
        switch (secondaryStorage.getType()) {
          case elasticsearch -> secondaryStorage.getElasticsearch();
          case opensearch -> secondaryStorage.getOpensearch();
          default -> null;
        };

    if (database == null) {
      return;
    }

    override.setPolicyName(database.getHistory().getPolicyName());
  }
}
