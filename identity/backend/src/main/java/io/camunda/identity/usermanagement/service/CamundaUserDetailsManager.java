/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;

@Service
public class CamundaUserDetailsManager extends JdbcUserDetailsManager {

  // @formatter:off
  public static final String DEF_USERS_QUERY = "select username from users ";
  public static final String DEF_USERS_GROUPS_QUERY =
      "select g.id, g.group_name"
          + " from groups g, group_members gm"
          + " where gm.username = ? and g.id = gm.group_id";

  // @formatter:on

  public CamundaUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableGroups(true);
    setEnableAuthorities(true);
  }

  public List<UserDetails> loadUsers() {
    return getJdbcTemplate().queryForList(DEF_USERS_QUERY, String.class).stream()
        .map(this::loadUserByUsername)
        .toList();
  }

  public List<String> loadUserGroups(final String username) {
    return getJdbcTemplate()
        .query(DEF_USERS_GROUPS_QUERY, new String[] {username}, (rs, rowNum) -> rs.getString(2));
  }

  public Integer findGroupId(final String group) {
    assert getJdbcTemplate() != null;
    return getJdbcTemplate().queryForObject(DEF_FIND_GROUP_ID_SQL, Integer.class, group);
  }
}
