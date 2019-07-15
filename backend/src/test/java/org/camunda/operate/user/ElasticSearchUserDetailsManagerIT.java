/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;


import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.TestApplication;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestApplication.class})
public class ElasticSearchUserDetailsManagerIT extends OperateIntegrationTest {

  @Autowired
  OperateProperties operateProperties;
  
  @Autowired
  UserStorage userStorage;
  UserDetailsManager userManagerService;
  @Before
  public void setUp() {
    //TODO: Factor passwordEncoder out
    operateProperties.setUsername("test-user");
    operateProperties.setPassword("test-password");
    userManagerService = ElasticSearchUserDetailsManager.buildWith(new BCryptPasswordEncoder(), operateProperties, userStorage);
  }
  
  @Test
  public void testStandardUserAndActUsersExists() {
    assertThat(userManagerService.userExists("test-user")).isTrue();
    assertThat(userManagerService.userExists("act")).isTrue();
  }
  

}
