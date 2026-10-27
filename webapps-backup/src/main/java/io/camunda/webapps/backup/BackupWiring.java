/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;

/** Bundles the backup wiring needed by a {@link BackupService} for a single physical tenant. */
public record BackupWiring(
    BackupPriorities priorities,
    BackupRepositoryProps repositoryProps,
    BackupRepository repository) {}
