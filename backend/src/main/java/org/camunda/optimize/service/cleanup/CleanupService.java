/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class CleanupService {

  public abstract boolean isEnabled();

  public abstract void doCleanup(final OffsetDateTime startTime);

  public void verifyConfiguredKeysAreKnownDefinitionKeys(final Set<String> knownDefinitionKeys,
                                                         final Set<String> specificDefinitionConfigKeys) {
    final Set<String> knownConfiguredKeys = specificDefinitionConfigKeys.stream()
      .filter(knownDefinitionKeys::contains)
      .collect(Collectors.toSet());
    specificDefinitionConfigKeys.removeAll(knownConfiguredKeys);
    if (!specificDefinitionConfigKeys.isEmpty()) {
      log.warn("History Cleanup Configuration contains definition keys for which there is no "
                 + "definition imported yet. The keys without a match in the database are: "
                 + specificDefinitionConfigKeys);
    }
  }

}
