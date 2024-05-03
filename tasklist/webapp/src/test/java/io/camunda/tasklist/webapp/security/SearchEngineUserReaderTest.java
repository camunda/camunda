/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.tasklist.webapp.security.oauth.IdentityTenantAwareJwtAuthenticationToken;
import io.camunda.tasklist.webapp.security.se.SearchEngineUserReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SearchEngineUserReaderTest {

  @InjectMocks private SearchEngineUserReader searchEngineUserReader;

  @Test
  public void shouldReturnExceptionWhenGettingToken() {
    final Jwt jwt = mock(Jwt.class);
    final var jwtAuthenticationToken = mock(IdentityTenantAwareJwtAuthenticationToken.class);

    assertThatThrownBy(() -> searchEngineUserReader.getUserToken(jwtAuthenticationToken))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Get token is not supported for Identity authentication");
  }
}
