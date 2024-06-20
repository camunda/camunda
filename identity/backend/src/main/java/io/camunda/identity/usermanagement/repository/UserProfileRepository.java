/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.repository;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.model.Profile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<Profile, Long> {

  @Query(
      """
          select new io.camunda.identity.usermanagement.CamundaUser(users.id, users.username, profiles.email, users.enabled) \
          from User users \
          left join Profile profiles on users.id = profiles.id""")
  List<CamundaUser> findAllUsers();

  @Query(
      """
          select new io.camunda.identity.usermanagement.CamundaUser(users.id, users.username, profiles.email, users.enabled) \
          from User users \
          left join Profile profiles on users.id = profiles.id
          where users.username = :username
          """)
  CamundaUser findByUsername(@Param("username") final String username);

  @Query(
      """
          select new io.camunda.identity.usermanagement.CamundaUser(users.id, users.username, profiles.email, users.enabled) \
          from User users \
          left join Profile profiles on users.id = profiles.id
          where users.id = :id
          """)
  Optional<CamundaUser> findUserById(@Param("id") final Long id);

  @Query(
      """
          select new io.camunda.identity.usermanagement.CamundaUser(users.id, users.username, profiles.email, users.enabled) \
          from User users \
          left join Profile profiles on users.id = profiles.id
          where users.username in (:usernames)
          """)
  List<CamundaUser> findAllByUsernameIn(@Param("usernames") List<String> usernames);
}
