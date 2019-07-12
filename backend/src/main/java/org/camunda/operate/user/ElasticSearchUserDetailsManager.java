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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.util.Assert;

public class ElasticSearchUserDetailsManager implements UserDetailsManager {

  private OperateProperties operateProperties;
  private UserStorage userStorage;

  private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  
  public static ElasticSearchUserDetailsManager buildWith(PasswordEncoder passwordEncoder, OperateProperties operateProperties,UserStorage userStorage) {
    return new ElasticSearchUserDetailsManager()
        .setUserStorage(userStorage)
        .setOperateProperties(operateProperties)
        .setPasswordEncoder(passwordEncoder)
        .build();
  }
  
  private ElasticSearchUserDetailsManager setUserStorage(UserStorage userStorage) {
    this.userStorage = userStorage;
    return this;
  }

  private ElasticSearchUserDetailsManager() {
  }
  
  public ElasticSearchUserDetailsManager setOperateProperties(OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
    return this;
  }

  public ElasticSearchUserDetailsManager createUserFrom(String username,String password,String role) {
    String passwordEncoded = passwordEncoder.encode(password);
    UserDetails userDetails = User.builder()
      .username(username)
      .password(passwordEncoded)
      .roles(role)
      .build();
    createUser(userDetails);
    return this;
  }

  public ElasticSearchUserDetailsManager setPasswordEncoder(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
    return this;
  }
  
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserEntity userEntity = userStorage.getUserByName(username);
    return new User(userEntity.getUsername(), userEntity.getPassword(),true,
        true, true,
        true, toAuthorities(userEntity.getRoles()));
  }

  private Collection<? extends GrantedAuthority> toAuthorities(String role) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    Assert.isTrue(!role.startsWith("ROLE_"), () -> role
          + " cannot start with ROLE_ (it is automatically added)");
    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    return authorities;
  }

  @Override
  public void createUser(UserDetails user) {
    userStorage.createUser(UserEntity.fromUserDetails(user));
  }

  @Override
  public void updateUser(UserDetails user) {
    userStorage.saveUser(UserEntity.fromUserDetails(user));
  }

  @Override
  public void deleteUser(String username) {
    userStorage.deleteUserById(userStorage.getUserByName(username).getId());
  }

  @Override
  public void changePassword(String oldPassword, String newPassword) {
    throw new UnsupportedOperationException("Change password is not supported yet.");
  }

  @Override
  public boolean userExists(String username) {
    try {
      return userStorage.getUserByName(username)!=null;
    }catch(Throwable t) {
      return false;
    }
  }

  public ElasticSearchUserDetailsManager build() {
    String username = operateProperties.getUsername();
    if(!userExists(username)) {
      createUserFrom(operateProperties.getUsername(), operateProperties.getPassword(), "USER");
    }   
    if(!userExists("act")) {
      createUserFrom("act", "act", "ACTRADMIN");
    }
    return this;
  }
}
