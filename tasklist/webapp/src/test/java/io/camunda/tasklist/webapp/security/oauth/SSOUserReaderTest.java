/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.security.sso.SSOUserReader;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SSOUserReaderTest {

  @InjectMocks private SSOUserReader ssoUserReader;

  @Test
  public void shouldReturnToken() {
    final Jwt jwt = mock(Jwt.class);
    final var authenticationToken = mock(TokenAuthentication.class);
    when(authenticationToken.getNewTokenByRefreshToken()).thenReturn("123");
    assertThat(ssoUserReader.getUserToken(authenticationToken)).isNotEmpty();
  }
}
