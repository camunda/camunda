/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.usermanagement.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;

@Service
public class IdentityLocalUserDetailsManager extends JdbcUserDetailsManager {

  // @formatter:off
  public static final String DEF_USERS_QUERY = "select username from users ";

  // @formatter:on

  public IdentityLocalUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableGroups(false);
    setEnableAuthorities(true);
  }

  public List<UserDetails> loadUsers() {
    return getJdbcTemplate().query(DEF_USERS_QUERY, this::mapUsernameToUser);
  }

  private UserDetails mapUsernameToUser(final ResultSet resultSet, final int i)
      throws SQLException {
    return loadUserByUsername(resultSet.getString("username"));
  }
}
