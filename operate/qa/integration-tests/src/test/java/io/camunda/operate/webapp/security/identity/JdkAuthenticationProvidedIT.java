/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.oauth2.IdentityJwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.IdentityOAuth2WebConfigurer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityAuthentication.class,
      OperateProperties.class,
      IdentityOAuth2WebConfigurer.class,
      IdentityWebSecurityConfig.class,
      OperateProfileService.class
    },
    properties = {
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:2222/auth"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class JdkAuthenticationProvidedIT {
  @Autowired private IdentityOAuth2WebConfigurer identityOAuth2WebConfigurer;

  @SpyBean private IdentityConfiguration identityConfiguration;

  @MockBean private IdentityJwt2AuthenticationTokenConverter converter;

  @Test
  public void shouldReturnProvidedUrlJdkAuthWhenSpringPropertySet() {
    when(identityConfiguration.getIssuerBackendUrl()).thenReturn("http://localhost:1111");

    final String result =
        (String)
            (ReflectionTestUtils.invokeGetterMethod(
                identityOAuth2WebConfigurer, "getJwkSetUriProperty"));

    assertThat(result).isEqualTo("http://localhost:2222/auth");
  }
}
