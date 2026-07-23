/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

/**
 * Checks whether any Optimize entity document references the given user ID, and rewrites all
 * occurrences from oldUserId to newUserId across every entity-owning index.
 */
public interface UserIdMigrationRepository {

  /**
   * Returns true if at least one document in any entity index still references the given user ID
   * (as owner, lastModifier, or collection role member). Used to skip the migration when it has
   * already been applied.
   */
  boolean hasDocumentsWithUserId(String userId);

  /**
   * Rewrites every occurrence of oldUserId to newUserId across all entity-owning indices:
   * collection role membership, owner, and lastModifier fields.
   */
  void migrateUserId(String oldUserId, String newUserId);
}
