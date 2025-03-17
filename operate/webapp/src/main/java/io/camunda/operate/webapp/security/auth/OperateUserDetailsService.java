/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import static io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder.aCamundaUser;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.entities.UserEntity;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.UserStore;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile({
  "!"
      + OperateProfileService.LDAP_AUTH_PROFILE
      + " & !"
      + OperateProfileService.SSO_AUTH_PROFILE
      + " & !"
      + OperateProfileService.IDENTITY_AUTH_PROFILE
      + " & !"
      + OperateProfileService.CONSOLIDATED_AUTH
})
public class OperateUserDetailsService implements UserDetailsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperateUserDetailsService.class);
  private static final String READ_ONLY_USER = "view";
  private static final String ACT_USERNAME = "act";
  private static final String ACT_PASSWORD = ACT_USERNAME;
  @Autowired private UserStore userStore;
  @Autowired private OperateProperties operateProperties;

  @Bean
  @Primary
  public PasswordEncoder getPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  public void initializeUsers() {
    if (needsToCreateUser()) {
      final String userId = operateProperties.getUserId();
      if (!userExists(userId)) {
        addUserWith(
            userId,
            operateProperties.getDisplayName(),
            operateProperties.getPassword(),
            operateProperties.getRoles());
      }
      if (!userExists(READ_ONLY_USER)) {
        addUserWith(READ_ONLY_USER, READ_ONLY_USER, READ_ONLY_USER, List.of(Role.USER.name()));
      }
      if (!userExists(ACT_USERNAME)) {
        addUserWith(ACT_USERNAME, ACT_USERNAME, ACT_PASSWORD, List.of(Role.OPERATOR.name()));
      }
    }
  }

  private boolean needsToCreateUser() {
    if (DatabaseInfo.isOpensearch()) {
      return operateProperties.getOpensearch().isCreateSchema();
    } else {
      return operateProperties.getElasticsearch().isCreateSchema();
    }
  }

  private OperateUserDetailsService addUserWith(
      final String userId,
      final String displayName,
      final String password,
      final List<String> roles) {
    LOGGER.info("Create user in {} for userId {}", DatabaseInfo.getCurrent().name(), userId);
    final String passwordEncoded = getPasswordEncoder().encode(password);
    final UserEntity userEntity =
        new UserEntity()
            .setId(userId)
            .setUserId(userId)
            .setDisplayName(displayName)
            .setPassword(passwordEncoded)
            .setRoles(roles);
    userStore.save(userEntity);
    return this;
  }

  @Override
  public CamundaUser loadUserByUsername(final String userId) {
    try {
      final UserEntity userEntity = userStore.getById(userId);
      return aCamundaUser()
          .withName(userEntity.getDisplayName())
          .withUsername(userEntity.getUserId())
          .withPassword(userEntity.getPassword())
          .withAuthorities(userEntity.getRoles())
          .build();
    } catch (final NotFoundException e) {
      throw new UsernameNotFoundException(
          String.format("User with userId '%s' not found.", userId), e);
    }
  }

  private boolean userExists(final String userId) {
    try {
      return userStore.getById(userId) != null;
    } catch (final Exception t) {
      return false;
    }
  }
}
