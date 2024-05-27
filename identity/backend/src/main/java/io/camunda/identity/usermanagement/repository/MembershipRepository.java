/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.repository;

import io.camunda.identity.usermanagement.Group;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {
  public static final String DEF_USERS_GROUPS_QUERY =
      "select g.id, g.group_name"
          + " from groups g, group_members gm"
          + " where gm.username = ? and g.id = gm.group_id";

  private final JdbcTemplate jdbcTemplate;

  public MembershipRepository(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Group> loadUserGroups(final String username) {
    return jdbcTemplate.query(
        DEF_USERS_GROUPS_QUERY,
        new String[] {username},
        (rs, rowNum) -> new Group(rs.getInt(1), rs.getString(2)));
  }
}
