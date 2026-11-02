/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.script;

public interface UserIdMigrationScriptFactory {

  /**
   * A collection can already hold a role for the new ID, for example when the user was added
   * through the user search before the migration. Two roles for one user break role updates, so the
   * script keeps one role with the higher permission.
   */
  static String createMigrateCollectionRolesScript() {
    return "def roles = ctx._source.data.roles;"
        + "def oldRole = null;"
        + "def newRole = null;"
        + "for (def role : roles) {"
        + "  if (role.identity.id == params.oldId) { oldRole = role; }"
        + "  else if (role.identity.id == params.newId) { newRole = role; }"
        + "}"
        + "if (oldRole != null && newRole == null) {"
        + "  oldRole.identity.id = params.newId;"
        + "  oldRole.id = 'USER:' + params.newId;"
        + "} else if (oldRole != null) {"
        + "  def rank = ['viewer': 0, 'editor': 1, 'manager': 2];"
        + "  if (rank.getOrDefault(oldRole.role, -1) > rank.getOrDefault(newRole.role, -1)) {"
        + "    newRole.role = oldRole.role;"
        + "  }"
        + "  roles.removeIf(role -> role.identity.id == params.oldId);"
        + "}";
  }

  static String createMigrateOwnerScript() {
    return "if (ctx._source.owner == params.oldId) { ctx._source.owner = params.newId; }"
        + "if (ctx._source.lastModifier == params.oldId) { ctx._source.lastModifier = params.newId; }";
  }
}
