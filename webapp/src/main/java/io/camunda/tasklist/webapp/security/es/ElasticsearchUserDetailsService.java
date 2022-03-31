/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IAM_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
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
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IAM_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE)
public class ElasticsearchUserDetailsService implements UserDetailsService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchUserDetailsService.class);

  private static final String ACT_USERNAME = "act", ACT_PASSWORD = ACT_USERNAME;
  private static final String READ_ONLY_USER = "view";
  @Autowired private UserStorage userStorage;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private PasswordEncoder passwordEncoder;

  @Bean
  public PasswordEncoder getPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  public void initializeUsers() {
    if (tasklistProperties.getElasticsearch().isCreateSchema()) {
      final String userId = tasklistProperties.getUserId();
      final String displayName = tasklistProperties.getDisplayName();
      final String password = tasklistProperties.getPassword();
      final List<String> roles = tasklistProperties.getRoles();
      if (!userExists(userId)) {
        addUserWith(userId, displayName, password, roles);
      }
      if (!userExists(READ_ONLY_USER)) {
        addUserWith(READ_ONLY_USER, READ_ONLY_USER, READ_ONLY_USER, List.of(Role.READER.name()));
      }
      if (!userExists(ACT_USERNAME)) {
        addUserWith(ACT_USERNAME, ACT_USERNAME, ACT_PASSWORD, List.of(Role.OPERATOR.name()));
      }
    }
  }

  private boolean userExists(String userId) {
    try {
      return userStorage.getByUserId(userId) != null;
    } catch (Exception t) {
      return false;
    }
  }

  ElasticsearchUserDetailsService addUserWith(
      final String userId,
      final String displayName,
      final String password,
      final List<String> roles) {
    LOGGER.info("Create user in Elasticsearch for userId {}", userId);
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity userEntity =
        new UserEntity()
            .setId(userId)
            .setUserId(userId)
            .setDisplayName(displayName)
            .setPassword(passwordEncoded)
            .setRoles(roles);
    userStorage.create(userEntity);
    return this;
  }

  @Override
  public User loadUserByUsername(String username) throws UsernameNotFoundException {
    try {
      final UserEntity userEntity = userStorage.getByUserId(username);
      return new User(
              userEntity.getUserId(),
              userEntity.getPassword(),
              map(userEntity.getRoles(), Role::fromString))
          .setDisplayName(userEntity.getDisplayName())
          .setRoles(map(userEntity.getRoles(), Role::fromString));
    } catch (NotFoundException e) {
      throw new UsernameNotFoundException(
          String.format("User with user id '%s' not found.", username), e);
    }
  }
}
