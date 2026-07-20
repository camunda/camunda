/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

/**
 * Marker for the earliest backup tier: state/progress-tracking indices (e.g. import position,
 * pending history deletions), and indices that are roots of an entity hierarchy whose dependents
 * live in a later tier (e.g. process definitions, decision requirements/decisions, forms). Both
 * categories must be snapshotted first: state-tracking indices because later tiers are read
 * relative to them, and hierarchy roots because a concurrent cascade-delete or archiver run could
 * otherwise remove them before a later tier captures them, leaving their already-captured
 * dependents dangling on restore.
 */
public interface Prio1Backup extends BackupPriority {}
