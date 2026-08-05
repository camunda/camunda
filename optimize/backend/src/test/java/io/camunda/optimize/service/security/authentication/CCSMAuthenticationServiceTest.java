/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CCSMAuthConfiguration;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CCSMAuthenticationServiceTest {

  private static final URI CALLBACK_URI =
      URI.create("http://localhost:8090/api/authentication/callback?code=someCode");

  @Mock private SessionService sessionService;
  @Mock private AuthCookieService authCookieService;
  @Mock private CCSMTokenService ccsmTokenService;
  @Mock private ConfigurationService configurationService;

  private CCSMAuthenticationService authenticationService;
  private HttpServletResponse mockResponse;

  @BeforeEach
  public void setup() {
    final CCSMAuthConfiguration ccsmAuthConfiguration = new CCSMAuthConfiguration();
    final AuthConfiguration authConfiguration = new AuthConfiguration();
    authConfiguration.setCcsmAuthConfiguration(ccsmAuthConfiguration);
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(configurationService.getContextPath()).thenReturn(Optional.empty());

    authenticationService =
        new CCSMAuthenticationService(
            sessionService, authCookieService, ccsmTokenService, configurationService);
    mockResponse = mock(HttpServletResponse.class);
  }

  @Test
  public void shouldSendForbiddenErrorWhenUserIsNotAuthorized() throws IOException {
    // given
    when(ccsmTokenService.exchangeAuthCode(any(), any()))
        .thenThrow(new NotAuthorizedException("User has no Optimize access"));

    // when
    authenticationService.loginCallback(
        new AuthCodeDto("someCode", "someState", null), CALLBACK_URI, mockResponse);

    // then
    verify(mockResponse)
        .sendError(
            HttpStatus.FORBIDDEN.value(),
            "User has no authorization to access Optimize. Please check your Identity configuration");
    verify(mockResponse, never()).sendRedirect(anyString());
  }
}
