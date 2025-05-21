/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration.Type;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityAuthentication.class,
      CamundaSecurityProperties.class
    },
    properties = {
      "camunda.operate.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class AuthenticationNoJWTTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired @InjectMocks private IdentityAuthentication identityAuthentication;
  @Mock private Identity identity;

  @BeforeEach
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
  }

  @Test
  public void shouldRequestNewAccessToken() {
    // given
    final String accessToken = "abc";
    final String refreshToken = "def";
    final String tokenType = Type.MICROSOFT.name();
    final Tokens tokens = new Tokens(accessToken, refreshToken, 5000L, "", tokenType);

    // when
    identityAuthentication.authenticate(tokens);
    // then

  }
}
