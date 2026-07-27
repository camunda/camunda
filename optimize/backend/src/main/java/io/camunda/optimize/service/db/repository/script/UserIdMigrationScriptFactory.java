/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.script;

public interface UserIdMigrationScriptFactory {

  static String createMigrateCollectionRolesScript() {
    return "for (def role : ctx._source.data.roles) {"
        + "  if (role.identity.id == params.oldId) {"
        + "    role.identity.id = params.newId;"
        + "    role.id = 'USER:' + params.newId;"
        + "  }"
        + "}";
  }

  static String createMigrateOwnerScript() {
    return "if (ctx._source.owner == params.oldId) { ctx._source.owner = params.newId; }"
        + "if (ctx._source.lastModifier == params.oldId) { ctx._source.lastModifier = params.newId; }";
  }
}
