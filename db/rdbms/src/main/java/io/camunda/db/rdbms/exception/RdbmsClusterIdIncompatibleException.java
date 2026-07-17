/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

import io.camunda.zeebe.util.exception.UnrecoverableException;

/**
 * Thrown when this cluster's ID does not match the one previously recorded in the RDBMS schema.
 * Only enforced when auto-DDL is enabled; {@code NoopSchemaManager} skips the check entirely.
 *
 * <p>Extends {@link UnrecoverableException} since a mismatch won't resolve itself on retry.
 */
public class RdbmsClusterIdIncompatibleException extends UnrecoverableException {

  public RdbmsClusterIdIncompatibleException(
      final String previousClusterId, final String clusterId) {
    super(
        ("Secondary storage schema was previously initialized by cluster '%s', but this "
                + "cluster's ID is '%s'. Pointing a cluster at storage belonging to a different "
                + "installation can corrupt data. If this is an intentional re-pointing, set "
                + "clusterIdCheckRestrictionEnabled=false to bypass this check.")
            .formatted(previousClusterId, clusterId));
  }
}
