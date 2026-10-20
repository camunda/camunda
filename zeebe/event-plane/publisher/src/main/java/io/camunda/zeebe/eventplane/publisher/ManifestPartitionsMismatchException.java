/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.segment.Identity;

/** A manifest that holds other partitions than the log's one: it isn't the log's part. */
public final class ManifestPartitionsMismatchException extends MismatchedManifestException {

  ManifestPartitionsMismatchException(final Identity log, final HistoryManifest manifest) {
    super(
        "Expected history %s to hold only partition %d, but it holds %s"
            .formatted(manifest.historyId(), log.partition(), manifest.partitions().keySet()),
        log,
        manifest);
  }
}
