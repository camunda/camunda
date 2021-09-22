/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.es;


import static io.camunda.operate.util.CollectionUtil.map;

import io.camunda.operate.webapp.security.Role;
import java.util.List;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.OperateURIs;
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
@Profile("!" + OperateURIs.LDAP_AUTH_PROFILE + " & ! " + OperateURIs.SSO_AUTH_PROFILE)
public class ElasticSearchUserDetailsService implements UserDetailsService {

  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchUserDetailsService.class);

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
      String username = operateProperties.getUsername();
      if (!userExists(username)) {
        addUserWith(username, operateProperties.getPassword(), operateProperties.getRoles());
      }
      if (!userExists(READ_ONLY_USER)) {
        addUserWith(READ_ONLY_USER, READ_ONLY_USER, List.of(Role.USER.name()));
      }
      if (!userExists(ACT_USERNAME)) {
        addUserWith(ACT_USERNAME, ACT_PASSWORD, List.of(Role.OWNER.name()));
      }
    }
  }

  private ElasticSearchUserDetailsService addUserWith(String username, String password, List<String> roles) {
    logger.info("Create user in ElasticSearch for username {}",username);
    String passwordEncoded = passwordEncoder.encode(password);
    userStorage.create(UserEntity.from(username, passwordEncoded, roles));
    return this;
  }

  @Override
  public User loadUserByUsername(String username) {
    try {
      UserEntity userEntity = userStorage.getByName(username);
      return new User(userEntity.getUsername(), userEntity.getPassword(),
          map(userEntity.getRoles(), Role::fromString))
          .setFirstname(userEntity.getFirstname())
          .setLastname(userEntity.getLastname());
    }catch(NotFoundException e) {
      throw new UsernameNotFoundException(String.format("User with username '%s' not found.",username),e);
    }
  }

  private boolean userExists(String username) {
    try {
      return userStorage.getByName(username)!=null;
    }catch(Exception t) {
      return false;
    }
  }

}
