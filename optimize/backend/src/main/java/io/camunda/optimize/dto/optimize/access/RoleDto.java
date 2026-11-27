/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.access;

import static io.camunda.optimize.service.db.schema.index.RoleIndex.DESCRIPTION;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN_PARENT;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.NAME;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.ROLE_ID;

import java.util.HashMap;
import java.util.Map;

public record RoleDto(String roleId, String name, String description) {

  /** Same id scheme as the Orchestration Cluster, which members use as their join parent. */
  public static String documentId(final String roleId) {
    return JOIN_PARENT + "-" + roleId;
  }

  public Map<String, Object> toDocument() {
    final Map<String, Object> document = new HashMap<>();
    document.put(ROLE_ID, roleId);
    document.put(NAME, name);
    document.put(DESCRIPTION, description);
    document.put(JOIN, Map.of("name", JOIN_PARENT));
    return document;
  }

  public static RoleDto fromDocument(final Map<String, Object> document) {
    return new RoleDto(
        (String) document.get(ROLE_ID),
        (String) document.get(NAME),
        (String) document.get(DESCRIPTION));
  }
}
