/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;

public class ElasticSearchUserDetailsService implements UserDetailsService{

  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchUserDetailsService.class);

  private UserStorage userStorage;
  
  private PasswordEncoder passwordEncoder;

  public ElasticSearchUserDetailsService(OperateProperties operateProperties, UserStorage userStorage, PasswordEncoder passwordEncoder) {
    this.userStorage = userStorage;
    this.passwordEncoder = passwordEncoder;
    String username = operateProperties.getUsername();
    if(!userExists(username)) {
      createUserFrom(operateProperties.getUsername(), operateProperties.getPassword(), "USER");
    }   
    if(!userExists("act")) {
      createUserFrom("act", "act", "ACTRADMIN");
    }
  }

  protected ElasticSearchUserDetailsService createUserFrom(String username,String password,String role) {
    logger.info("Create user in ElasticSearch for username {}",username);
    String passwordEncoded = passwordEncoder.encode(password);
    UserDetails userDetails = User.builder()
      .username(username)
      .password(passwordEncoded)
      .roles(role)
      .build();
    userStorage.create(UserEntity.fromUserDetails(userDetails));
    return this;
  }
  
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserEntity userEntity = userStorage.getByName(username);
    return new User(userEntity.getUsername(), userEntity.getPassword(),true,
        true, true,
        true, toAuthorities(userEntity.getRoles()));
  }

  protected Collection<? extends GrantedAuthority> toAuthorities(String role) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    Assert.isTrue(!role.startsWith("ROLE_"), () -> role
          + " cannot start with ROLE_ (it is automatically added)");
    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    return authorities;
  }
  
  protected boolean userExists(String username) {
    try {
      return userStorage.getByName(username)!=null;
    }catch(Throwable t) {
      return false;
    }
  }
}