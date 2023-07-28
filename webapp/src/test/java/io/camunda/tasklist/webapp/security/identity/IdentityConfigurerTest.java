/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class IdentityConfigurerTest {
  public static final String KEYCLOAK_AUTHENTICATION = "KeycloakAuthentication";
  public static final String AUTH_0_AUTHENTICATION = "Auth0Authentication";
  @InjectMocks private IdentityConfigurer identityConfigurer;
  @Mock private TasklistProperties tasklistProperties;
  @Mock private Environment environment;

  @Test
  public void shouldCreateAuthenticationInstanceOfOKeycloakForIdentity() {
    // Given
    when(environment.getActiveProfiles())
        .thenReturn(new String[] {TasklistProfileService.IDENTITY_AUTH_PROFILE});
    when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
    when(tasklistProperties.getIdentity().getBaseUrl()).thenReturn("https://example.com");
    when(tasklistProperties.getIdentity().getIssuerUrl()).thenReturn("https://example.com/issuer");
    when(tasklistProperties.getIdentity().getIssuerBackendUrl())
        .thenReturn("https://example.com/issuer/backend");
    when(tasklistProperties.getIdentity().getClientId()).thenReturn("clientId");
    when(tasklistProperties.getIdentity().getClientSecret()).thenReturn("clientSecret");
    when(tasklistProperties.getIdentity().getAudience()).thenReturn("audience");

    // When
    final Identity identity = identityConfigurer.getIdentity();

    // Then
    // verifying from the class type if contains the type Keycloak (instanceOf not possible in this
    // case as refers to Proxy)
    assertTrue(identity.authentication().toString().contains(KEYCLOAK_AUTHENTICATION));
    assertFalse(identity.authentication().toString().contains(AUTH_0_AUTHENTICATION));
  }

  @Test
  public void shouldCreateAuthenticationInstanceOfOAuthForSSO() {
    // Given
    when(environment.getActiveProfiles())
        .thenReturn(new String[] {TasklistProfileService.SSO_AUTH_PROFILE});
    when(tasklistProperties.getIdentity()).thenReturn(mock(IdentityProperties.class));
    when(tasklistProperties.getIdentity().getBaseUrl()).thenReturn("https://example.com");

    // When
    final Identity identity = identityConfigurer.getIdentity();

    // Then
    // verifying from the class type if contains the type OAuth (instanceOf not possible in this
    // case as refers to Proxy)
    assertTrue(identity.authentication().toString().contains(AUTH_0_AUTHENTICATION));
    assertFalse(identity.authentication().toString().contains(KEYCLOAK_AUTHENTICATION));
  }
}
