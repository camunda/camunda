/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;

import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

public class ElasticSearchUserDetailsManager implements UserDetailsManager {

  private OperateProperties operateProperties;
  private RestHighLevelClient esClient;
  
  private UserDetailsManager delegate = new InMemoryUserDetailsManager();
  private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  
  public static ElasticSearchUserDetailsManager buildWith(PasswordEncoder passwordEncoder, OperateProperties operateProperties,RestHighLevelClient esClient) {
    return new ElasticSearchUserDetailsManager()
        .setEsClient(esClient)
        .setOperateProperties(operateProperties)
        .setPasswordEncoder(passwordEncoder)
        .build();
  }
  
  private ElasticSearchUserDetailsManager() {
  }
  
  public ElasticSearchUserDetailsManager setOperateProperties(OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
    return this;
  }

  public ElasticSearchUserDetailsManager setEsClient(RestHighLevelClient esClient) {
    this.esClient = esClient;
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
    return delegate.loadUserByUsername(username);
  }

  @Override
  public void createUser(UserDetails user) {
    delegate.createUser(user);
  }

  @Override
  public void updateUser(UserDetails user) {
    delegate.updateUser(user);
  }

  @Override
  public void deleteUser(String username) {
    delegate.deleteUser(username);
  }

  @Override
  public void changePassword(String oldPassword, String newPassword) {
   delegate.changePassword(oldPassword, newPassword);
  }

  @Override
  public boolean userExists(String username) {
    return delegate.userExists(username);
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
