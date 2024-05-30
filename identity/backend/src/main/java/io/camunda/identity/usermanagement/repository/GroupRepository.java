/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.repository;

import static org.springframework.security.provisioning.JdbcUserDetailsManager.DEF_FIND_GROUP_ID_SQL;

import io.camunda.identity.user.Group;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GroupRepository {
  public static final String DEF_FIND_ALL_GROUPS_SQL = "select id, group_name from groups";
  public static final String DEF_FIND_GROUP_BY_ID_SQL =
      "select id, group_name from groups where id = ?";
  public static final String DEF_DELETE_GROUP_BY_ID_SQL = "delete from groups where id = ?";

  private final JdbcTemplate jdbcTemplate;

  public GroupRepository(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Group> findAllGroups() {
    return jdbcTemplate.query(
        DEF_FIND_ALL_GROUPS_SQL, (rs, rowNum) -> new Group(rs.getInt(1), rs.getString(2)));
  }

  public Group findGroupByName(final String groupName) {
    final Integer id = jdbcTemplate.queryForObject(DEF_FIND_GROUP_ID_SQL, Integer.class, groupName);
    return new Group(id, groupName);
  }

  public Group findGroupById(final Integer groupId) {
    return jdbcTemplate
        .query(
            DEF_FIND_GROUP_BY_ID_SQL,
            new Object[] {groupId},
            (rs, rowNum) -> new Group(rs.getInt(1), rs.getString(2)))
        .getFirst();
  }

  public void deleteGroupById(final Integer groupId) {
    final int numUpdatedRows = jdbcTemplate.update(DEF_DELETE_GROUP_BY_ID_SQL, groupId);
    if (numUpdatedRows != 1) {
      throw new RuntimeException("group.notFound");
    }
  }
}
