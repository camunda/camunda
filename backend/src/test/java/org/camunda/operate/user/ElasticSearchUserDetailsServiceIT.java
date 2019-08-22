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

public class ElasticSearchUserDetailsServiceIT extends OperateIntegrationTest {

  @Autowired
  OperateProperties operateProperties;
  
  @Autowired
  ElasticSearchUserDetailsService userDetailsService;
  
  @Test
  public void testAddConfigurationAndActUserWhenNotExistsToElasticSearch() {
    String username = operateProperties.getUsername();
    UserDetails testUser = userDetailsService.loadUserByUsername(username);
    assertThat(testUser.getUsername()).isEqualTo(username);
    UserDetails actUser = userDetailsService.loadUserByUsername("act");
    assertThat(actUser.getUsername()).isEqualTo("act");
  }
  
}
