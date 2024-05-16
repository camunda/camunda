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
}
