/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Validates that if archiver-less is enabled we are using an allowed secondary storage type.
 *
 * <p>Only Elasticsearch and Opensearch are currently supported for archiver-less.
 *
 * <p>RDBMS secondary storage is specifically blocked as archiver-less is not supported there yet
 * and we want to avoid potential issues in the future. i.e. if the engine is already generating
 * storage ordinals when we do an upgrade this could cause complications.
 */
@NullMarked
class ArchiverlessSecondaryStorageValidation implements CrossTenantValidation {
  private static final Set<SecondaryStorageType> ALLOWED_ARCHIVERLESS_SECONDARY_STORAGE_TYPES =
      Set.of(SecondaryStorageType.elasticsearch, SecondaryStorageType.opensearch);

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    for (final Map.Entry<String, Camunda> entry : resolvedByTenant.entrySet()) {
      final String tenantId = entry.getKey();
      final Camunda camunda = entry.getValue();

      if (camunda.getProcessing().getEngine().getStorageOrdinals().isEnableArchiverless()) {
        final var secondaryStorageType = camunda.getData().getSecondaryStorage().getType();
        if (!ALLOWED_ARCHIVERLESS_SECONDARY_STORAGE_TYPES.contains(secondaryStorageType)) {
          throw new UnifiedConfigurationException(
              String.format(
                  "Tenant '%s' is configured with secondary storage %s, which is not supported when archiver-less is enabled.",
                  tenantId, secondaryStorageType));
        }
      }
    }
  }
}
