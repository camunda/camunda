/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.time.OffsetDateTime;
import java.util.Set;

public interface CleanupService {
  boolean isEnabled();
  void doCleanup(final OffsetDateTime startTime);

  static void enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(final Set<String> knownDefinitionKeys,
                                                                            final Set<String> specificDefinitionConfigKeys) {
    specificDefinitionConfigKeys.removeAll(knownDefinitionKeys);
    if (!specificDefinitionConfigKeys.isEmpty()) {
      final String message =
        "History Cleanup Configuration contains definition keys for which there is no "
          + "definition imported yet, aborting this cleanup run to avoid unintended data loss."
          + "The keys without a match in the database are: " + specificDefinitionConfigKeys;
      throw new OptimizeConfigurationException(message);
    }
  }
}
