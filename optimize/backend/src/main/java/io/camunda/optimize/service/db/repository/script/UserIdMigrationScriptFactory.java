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
   * through the user search before the migration. Earlier migrations renamed roles in place and can
   * have left several roles for one ID. Two roles for one user break role updates, so the script
   * keeps one role with the highest permission of all user roles of both IDs. A group can have the
   * same ID as a user, so group roles stay as they are. A role without a type counts as a user
   * role, because the migration covered it before the type check. Only known permissions take part
   * in the maximum, and the kept role keeps its own value when no role of the user has one.
   */
  static String createMigrateCollectionRolesScript() {
    return "def roles = ctx._source.data.roles;"
        + "def rank = ['viewer': 0, 'editor': 1, 'manager': 2];"
        + "def kept = null;"
        + "def best = null;"
        + "for (def role : roles) {"
        + "  if ((role.identity.type == 'user' || role.identity.type == null)"
        + "      && (role.identity.id == params.oldId || role.identity.id == params.newId)) {"
        + "    if (kept == null) { kept = role; }"
        + "    if (rank.containsKey(role.role) && (best == null || rank[role.role] > rank[best])) {"
        + "      best = role.role;"
        + "    }"
        + "  }"
        + "}"
        + "if (kept != null) {"
        + "  def keep = kept;"
        + "  keep.identity.id = params.newId;"
        + "  keep.id = 'USER:' + params.newId;"
        + "  keep.identity.type = 'user';"
        + "  if (best != null) { keep.role = best; }"
        + "  roles.removeIf(role -> role !== keep"
        + "      && (role.identity.type == 'user' || role.identity.type == null)"
        + "      && (role.identity.id == params.oldId || role.identity.id == params.newId));"
        + "}";
  }

  static String createMigrateOwnerScript() {
    return "if (ctx._source.owner == params.oldId) { ctx._source.owner = params.newId; }"
        + "if (ctx._source.lastModifier == params.oldId) { ctx._source.lastModifier = params.newId; }";
  }
}
