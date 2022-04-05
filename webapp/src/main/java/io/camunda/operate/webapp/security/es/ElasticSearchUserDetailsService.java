/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.es;


import static io.camunda.operate.util.CollectionUtil.map;

import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.Role;
import java.util.List;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
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

@Configuration
@Profile("!" + OperateProfileService.LDAP_AUTH_PROFILE
    + " & !" + OperateProfileService.SSO_AUTH_PROFILE
    + " & !" + OperateProfileService.IDENTITY_AUTH_PROFILE
)
public class ElasticSearchUserDetailsService implements UserDetailsService {

  private static final Logger logger = LoggerFactory.getLogger(
      ElasticSearchUserDetailsService.class);

  private static final String ACT_USERNAME = "act", ACT_PASSWORD = ACT_USERNAME;
  private static final String READ_ONLY_USER = "view";

  @Autowired
  private UserStorage userStorage;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Bean
  public PasswordEncoder getPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  public void initializeUsers() {
    if (operateProperties.getElasticsearch().isCreateSchema()) {
      String userId = operateProperties.getUserId();
      if (!userExists(userId)) {
        addUserWith(userId, operateProperties.getDisplayName(), operateProperties.getPassword(),
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

  private ElasticSearchUserDetailsService addUserWith(final String userId, final String displayName,
      final String password, final List<String> roles) {
    logger.info("Create user in ElasticSearch for userId {}", userId);
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity userEntity = new UserEntity()
        .setId(userId)
        .setUserId(userId)
        .setDisplayName(displayName)
        .setPassword(passwordEncoded)
        .setRoles(roles);
    userStorage.create(userEntity);
    return this;
  }

  @Override
  public User loadUserByUsername(final String userId) {
    try {
      UserEntity userEntity = userStorage.getByUserId(userId);
      return new User(
          userEntity.getUserId(),
          userEntity.getDisplayName(),
          userEntity.getPassword(),
          map(userEntity.getRoles(), Role::fromString));
    } catch (NotFoundException e) {
      throw new UsernameNotFoundException(String.format("User with userId '%s' not found.", userId),
          e);
    }
  }

  private boolean userExists(final String userId) {
    try {
      return userStorage.getByUserId(userId) != null;
    } catch (Exception t) {
      return false;
    }
  }

}
