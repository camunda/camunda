/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.repository;

import io.camunda.identity.usermanagement.CamundaUser;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  public static final String DEF_USERS_QUERY =
      "select users.id as id, username, enabled, email "
          + "from users "
          + "left join profiles on users.id = profiles.id ";
  public static final String DEF_USER_QUERY = DEF_USERS_QUERY + " where username = ?";
  public static final String DEF_USER_BY_ID_QUERY = DEF_USERS_QUERY + " where users.id = ?";
  public static final String DEF_USER_ID_QUERY = "select id from users where username = ?";
  public static final String DEF_PROFILE_CREATE = "insert into profiles(id, email)values(?,?)";
  public static final String DEF_PROFILE_UPDATE = "update profiles set email = ? where id = ?";
  private final JdbcTemplate jdbcTemplate;

  public UserRepository(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<CamundaUser> loadUsers() {
    return jdbcTemplate.query(
        DEF_USERS_QUERY,
        (rs, rowNum) ->
            new CamundaUser(rs.getInt(1), rs.getString(2), rs.getString(4), rs.getBoolean(3)));
  }

  public void createProfile(final CamundaUser user) {
    final var id =
        jdbcTemplate.queryForObject(
            DEF_USER_ID_QUERY, new Object[] {user.username()}, Integer.class);
    jdbcTemplate.update(
        DEF_PROFILE_CREATE,
        (ps) -> {
          ps.setInt(1, id);
          ps.setString(2, user.email());
        });
  }

  public CamundaUser loadUser(final String username) {
    return jdbcTemplate
        .query(
            DEF_USER_QUERY,
            new Object[] {username},
            (rs, rowNum) ->
                new CamundaUser(rs.getInt(1), rs.getString(2), rs.getString(4), rs.getBoolean(3)))
        .get(0);
  }

  public void updateProfile(final CamundaUser user) {
    final var id =
        jdbcTemplate.queryForObject(
            DEF_USER_ID_QUERY, new Object[] {user.username()}, Integer.class);
    jdbcTemplate.update(
        DEF_PROFILE_UPDATE,
        (ps) -> {
          ps.setString(1, user.email());
          ps.setInt(2, id);
        });
  }

  public CamundaUser loadUserById(final Integer id) {
    return jdbcTemplate
        .query(
            DEF_USER_BY_ID_QUERY,
            new Object[] {id},
            (rs, rowNum) ->
                new CamundaUser(rs.getInt(1), rs.getString(2), rs.getString(4), rs.getBoolean(3)))
        .get(0);
  }
}
