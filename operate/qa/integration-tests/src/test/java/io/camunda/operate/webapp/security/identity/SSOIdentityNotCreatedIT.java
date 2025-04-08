/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.SecurityContextWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ActiveProfiles({"sso-auth"})
@SpringBootTest(
    classes = {
      IdentityConfigurer.class,
      DatabaseInfo.class,
      OperateProperties.class,
      SecurityContextWrapper.class
    },
    properties = {
      // when resource permissions are disabled, no Identity bean should be created
      OperateProperties.PREFIX + ".identity.resourcePermissionsEnabled = false"
    })
public class SSOIdentityNotCreatedIT {

  @Autowired private ApplicationContext applicationContext;

  @Test
  public void testIdentityIsCreated() {
    assertThatThrownBy(() -> applicationContext.getBean("saasIdentity"))
        .isInstanceOf(NoSuchBeanDefinitionException.class);
    assertThatThrownBy(() -> applicationContext.getBean("permissionsService"))
        .isInstanceOf(NoSuchBeanDefinitionException.class);
  }
}
