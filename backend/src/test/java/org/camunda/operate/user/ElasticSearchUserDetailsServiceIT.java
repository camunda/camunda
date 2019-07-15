/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;


import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class ElasticSearchUserDetailsServiceIT extends OperateIntegrationTest {

  @Autowired
  OperateProperties operateProperties;
  
  @Autowired
  UserStorage userStorage;
  
  @Test
  public void testAddConfigurationAndActUserWhenNotExistsToElasticSearch() {
    // Given
    operateProperties.setUsername("test-user");
    operateProperties.setPassword("test-password");
    
    // When
    UserDetailsService userDetailsService = new ElasticSearchUserDetailsService(operateProperties, userStorage, new BCryptPasswordEncoder());
    
    // Then
    UserDetails testUser = userDetailsService.loadUserByUsername("test-user");
    assertThat(testUser.getUsername()).isEqualTo("test-user");
    UserDetails actUser = userDetailsService.loadUserByUsername("act");
    assertThat(actUser.getUsername()).isEqualTo("act");
  }
  

}
