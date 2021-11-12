/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IAM_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.security.Role;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Configuration
@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IAM_AUTH_PROFILE)
public class ElasticsearchUserDetailsService implements UserDetailsService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchUserDetailsService.class);

  private static final String ACT_USERNAME = "act", ACT_PASSWORD = ACT_USERNAME;
  private static final String USER_DEFAULT_FIRSTNAME = "Demo";

  private static final String READ_ONLY_USER = "view";
  private static final String USER_DEFAULT_LASTNAME = "User";
  @Autowired private UserStorage userStorage;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private PasswordEncoder passwordEncoder;

  @Bean
  public PasswordEncoder getPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  public void initializeUsers() {
    if (tasklistProperties.getElasticsearch().isCreateSchema()) {
      final String username = tasklistProperties.getUsername();
      if (!userExists(username)) {
        addUserWith(username, tasklistProperties.getPassword(), tasklistProperties.getRoles());
      }
      if (!userExists(READ_ONLY_USER)) {
        addUserWith(READ_ONLY_USER, READ_ONLY_USER, List.of(Role.READER.name()));
      }
      if (!userExists(ACT_USERNAME)) {
        addUserWith(ACT_USERNAME, ACT_PASSWORD, List.of(Role.OPERATOR.name()));
      }
    }
  }

  private boolean userExists(String username) {
    try {
      return userStorage.getByName(username) != null;
    } catch (Exception t) {
      return false;
    }
  }

  ElasticsearchUserDetailsService addUserWith(
      String username, String password, List<String> roles) {
    LOGGER.info("Create user in ElasticSearch for username {}", username);
    final String passwordEncoded = passwordEncoder.encode(password);
    String firstname = username;
    if ("demo".equalsIgnoreCase(username)) {
      firstname = USER_DEFAULT_FIRSTNAME;
    }
    userStorage.create(
        UserEntity.from(username, passwordEncoded, roles)
            .setFirstname(firstname)
            .setLastname(USER_DEFAULT_LASTNAME));
    return this;
  }

  @Override
  public User loadUserByUsername(String username) throws UsernameNotFoundException {
    try {
      final UserEntity userEntity = userStorage.getByName(username);
      return new User(
              userEntity.getUsername(),
              userEntity.getPassword(),
              map(userEntity.getRoles(), Role::fromString))
          .setFirstname(userEntity.getFirstname())
          .setLastname(userEntity.getLastname())
          .setRoles(map(userEntity.getRoles(), Role::fromString));
    } catch (NotFoundException e) {
      throw new UsernameNotFoundException(
          String.format("User with username '%s' not found.", username), e);
    }
  }
}
