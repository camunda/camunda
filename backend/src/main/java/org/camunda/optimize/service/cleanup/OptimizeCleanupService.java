/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.time.OffsetDateTime;
import java.util.Set;

public interface OptimizeCleanupService {
  void doCleanup(final OffsetDateTime startTime);

  static void enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(Set<String> knownDefinitionKeys,
                                                                            Set<String> specificDefinitionConfigKeys) {
    specificDefinitionConfigKeys.removeAll(knownDefinitionKeys);
    if (specificDefinitionConfigKeys.size() > 0) {
      final String message =
        "History Cleanup Configuration contains definition keys for which there is no "
          + "definition imported yet, aborting this cleanup run to avoid unintended data loss."
          + "The keys without a match in the database are: " + specificDefinitionConfigKeys;
      throw new OptimizeConfigurationException(message);
    }
  }
}
