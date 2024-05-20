/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.authentication.basic;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
@Profile("identity-basic-auth")
public class CamundaUserDetailsManager extends JdbcUserDetailsManager {
  public static final String SELECT_USERNAMES_FROM_USERS = "SELECT username FROM users ";

  public CamundaUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableAuthorities(true);
  }

  public List<UserDetails> loadUsers() {
    return getJdbcTemplate().queryForList(SELECT_USERNAMES_FROM_USERS, String.class).stream()
        .map(this::loadUserByUsername)
        .toList();
  }
}
