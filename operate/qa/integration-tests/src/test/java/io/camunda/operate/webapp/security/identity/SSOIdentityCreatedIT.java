/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.identity.sdk.Identity;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.SecurityContextWrapper;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.security.impl.AuthorizationChecker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ActiveProfiles({"sso-auth"})
@SpringBootTest(
    classes = {
      IdentityConfigurer.class,
      DatabaseInfo.class,
      OperateProperties.class,
      CamundaSecurityProperties.class,
      TenantService.class,
      PermissionsService.class,
      SecurityContextWrapper.class
    },
    properties = {
      OperateProperties.PREFIX + ".identity.resourcePermissionsEnabled = true",
      "camunda.identity.baseUrl=http://IdentiyURL:8080"
    })
public class SSOIdentityCreatedIT {

  @MockBean AuthorizationChecker authorizationChecker;

  @Autowired
  @Qualifier("saasIdentity")
  private Identity identity;

  @Autowired private PermissionsService permissionsService;

  @Test
  public void testIdentityIsCreated() {
    assertThat(identity).isNotNull();
    assertThat(permissionsService).isNotNull();
  }
}
