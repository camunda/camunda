/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_BASIC;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.se.store.UserStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Configuration
@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE + " & !" + AUTH_BASIC)
/*
 * Required as primary for now due to a clashing bean in the always active Identity service classes.
 * In future versions this class will be removed and the Identity service will be used instead.
 */
@Primary
public class SearchEngineUserDetailsService implements UserDetailsService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SearchEngineUserDetailsService.class);

  @Autowired private UserStore userStore;

  @Autowired private TasklistProperties tasklistProperties;

  @Bean
  @Primary
  public PasswordEncoder getPasswordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  public void initializeUsers() {
    final boolean createSchema =
        TasklistProperties.ELASTIC_SEARCH.equalsIgnoreCase(tasklistProperties.getDatabase())
            ? tasklistProperties.getElasticsearch().isCreateSchema()
            : tasklistProperties.getOpenSearch().isCreateSchema();

    if (createSchema) {
      final String userId = tasklistProperties.getUserId();
      final String displayName = tasklistProperties.getDisplayName();
      final String password = tasklistProperties.getPassword();

      final String readerUserId = tasklistProperties.getReaderUserId();
      final String readerDisplayName = tasklistProperties.getReaderDisplayName();
      final String readerPassword = tasklistProperties.getReaderPassword();

      final String operatorUserId = tasklistProperties.getOperatorUserId();
      final String operatorDisplayName = tasklistProperties.getOperatorDisplayName();
      final String operatorPassword = tasklistProperties.getOperatorPassword();

      final List<String> roles = tasklistProperties.getRoles();
      if (!userExists(userId)) {
        addUserWith(userId, displayName, password, roles);
      }
      if (!userExists(readerUserId)) {
        addUserWith(readerUserId, readerDisplayName, readerPassword, List.of(Role.READER.name()));
      }
      if (!userExists(operatorUserId)) {
        addUserWith(
            operatorUserId, operatorDisplayName, operatorPassword, List.of(Role.OPERATOR.name()));
      }
    }
  }

  private boolean userExists(final String userId) {
    try {
      return userStore.getByUserId(userId) != null;
    } catch (final Exception t) {
      return false;
    }
  }

  SearchEngineUserDetailsService addUserWith(
      final String userId,
      final String displayName,
      final String password,
      final List<String> roles) {
    LOGGER.info("Create user with userId {}", userId);
    final String passwordEncoded = getPasswordEncoder().encode(password);
    final UserEntity userEntity =
        new UserEntity()
            .setId(userId)
            .setUserId(userId)
            .setDisplayName(displayName)
            .setPassword(passwordEncoded)
            .setRoles(roles);
    userStore.create(userEntity);
    return this;
  }

  @Override
  public CamundaUser loadUserByUsername(final String username) throws UsernameNotFoundException {
    try {
      final UserEntity userEntity = userStore.getByUserId(username);
      return new CamundaUser(
          userEntity.getDisplayName(),
          userEntity.getUserId(),
          userEntity.getPassword(),
          userEntity.getRoles());
    } catch (final NotFoundApiException e) {
      throw new UsernameNotFoundException(
          String.format("User with user id '%s' not found.", username), e);
    }
  }
}
