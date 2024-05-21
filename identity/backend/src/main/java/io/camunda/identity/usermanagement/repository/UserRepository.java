/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.repository;

import io.camunda.identity.record.CamundaUser;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  public static final String DEF_USERS_QUERY = "select username, enabled from users ";

  private final JdbcTemplate jdbcTemplate;

  public UserRepository(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<CamundaUser> loadUsers() {
    return jdbcTemplate.query(
        DEF_USERS_QUERY, (rs, rowNum) -> new CamundaUser(rs.getString(1), rs.getBoolean(2)));
  }
}
