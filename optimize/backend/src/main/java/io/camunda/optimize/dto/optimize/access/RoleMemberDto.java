/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.access;

import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN_CHILD;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.MEMBER_ID;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.MEMBER_TYPE;

import io.camunda.security.api.model.authz.EntityType;
import java.util.Locale;
import java.util.Map;

public record RoleMemberDto(EntityType memberType, String memberId) {

  /**
   * The Orchestration Cluster's id scheme, so one member is stored once per role, except that the
   * role and member ids are escaped: unescaped, role {@code a} with user {@code b-user-c} and role
   * {@code a-user-b} with user {@code c} would share an id and overwrite each other.
   */
  public String documentId(final String roleId) {
    return JOIN_CHILD
        + "-"
        + escape(roleId)
        + "-"
        + memberType.name().toLowerCase(Locale.ROOT)
        + "-"
        + escape(memberId);
  }

  private static String escape(final String id) {
    return id.replace("%", "%25").replace("-", "%2D");
  }

  public Map<String, Object> toDocument(final String roleId) {
    return Map.of(
        MEMBER_TYPE,
        memberType.name(),
        MEMBER_ID,
        memberId,
        JOIN,
        Map.of("name", JOIN_CHILD, "parent", RoleDto.documentId(roleId)));
  }

  public static RoleMemberDto fromDocument(final Map<String, Object> document) {
    return new RoleMemberDto(
        EntityType.valueOf((String) document.get(MEMBER_TYPE)), (String) document.get(MEMBER_ID));
  }
}
