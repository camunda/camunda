/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public abstract class CleanupService {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(CleanupService.class);

  public abstract boolean isEnabled();

  public abstract void doCleanup(final OffsetDateTime startTime);

  public void verifyConfiguredKeysAreKnownDefinitionKeys(
      final Set<String> knownDefinitionKeys, final Set<String> specificDefinitionConfigKeys) {
    final Set<String> knownConfiguredKeys =
        specificDefinitionConfigKeys.stream()
            .filter(knownDefinitionKeys::contains)
            .collect(Collectors.toSet());
    specificDefinitionConfigKeys.removeAll(knownConfiguredKeys);
    if (!specificDefinitionConfigKeys.isEmpty()) {
      log.warn(
          "History Cleanup Configuration contains definition keys for which there is no "
              + "definition imported yet. The keys without a match in the database are: "
              + specificDefinitionConfigKeys);
    }
  }
}
