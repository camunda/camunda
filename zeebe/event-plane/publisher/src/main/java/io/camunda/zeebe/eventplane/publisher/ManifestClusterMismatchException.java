/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.segment.LogId;

/** A manifest of another cluster than the log's. */
public final class ManifestClusterMismatchException extends MismatchedManifestException {

  ManifestClusterMismatchException(final LogId log, final HistoryManifest manifest) {
    super(
        "Expected a manifest of cluster '%s', but got one of '%s'"
            .formatted(log.cluster(), manifest.clusterId()),
        log,
        manifest);
  }
}
