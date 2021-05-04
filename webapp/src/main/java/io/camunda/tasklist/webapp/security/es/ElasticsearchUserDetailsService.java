/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.util.Arrays;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Configuration
@Component
@Profile("!" + SSO_AUTH_PROFILE)
public class ElasticsearchUserDetailsService implements UserDetailsService {

  static final String USER_ROLE = "USER";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchUserDetailsService.class);

  private static final String ACT_USERNAME = "act", ACT_PASSWORD = ACT_USERNAME;
  private static final String ACT_ADMIN_ROLE = "ACTRADMIN";
  private static final String USER_DEFAULT_FIRSTNAME = "Demo";
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
        addUserWith(username, tasklistProperties.getPassword(), USER_ROLE);
      }
      if (!userExists(ACT_USERNAME)) {
        addUserWith(ACT_USERNAME, ACT_PASSWORD, ACT_ADMIN_ROLE);
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

  ElasticsearchUserDetailsService addUserWith(String username, String password, String role) {
    LOGGER.info("Create user in ElasticSearch for username {}", username);
    final String passwordEncoded = passwordEncoder.encode(password);
    userStorage.create(
        UserEntity.from(username, passwordEncoded, role)
            .setFirstname(USER_DEFAULT_FIRSTNAME)
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
              toAuthorities(userEntity.getRole()))
          .setFirstname(userEntity.getFirstname())
          .setLastname(userEntity.getLastname());
    } catch (NotFoundException e) {
      throw new UsernameNotFoundException(
          String.format("User with username '%s' not found.", username), e);
    }
  }

  private Collection<? extends GrantedAuthority> toAuthorities(String role) {
    return Arrays.asList(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
