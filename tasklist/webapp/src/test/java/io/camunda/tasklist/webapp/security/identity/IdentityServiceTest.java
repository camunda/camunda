/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

  @Mock private Identity identity;
  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();

  @InjectMocks private IdentityService instance;

  private static Stream<Arguments> getRedirectUriWhenTasklistIdentityRootUrlNotProvidedTestData() {
    return Stream.of(
        of("http", 80, "/some-path", "http://localhost/some-path/identity-callback"),
        of("http", 8089, "", "http://localhost:8089/identity-callback"),
        of("https", 443, "", "https://localhost/identity-callback"),
        of(
            "https",
            9999,
            "/899f3de9-b907-4b7f-9fb7-6925bb5b0a0e",
            "https://localhost:9999/identity-callback?uuid=899f3de9-b907-4b7f-9fb7-6925bb5b0a0e"));
  }

  @ParameterizedTest
  @MethodSource("getRedirectUriWhenTasklistIdentityRootUrlNotProvidedTestData")
  void getRedirectUriWhenTasklistIdentityRootUrlNotProvided(
      String scheme, int port, String path, String expected) {
    // given
    final var req = mock(HttpServletRequest.class);
    when(req.getScheme()).thenReturn(scheme);
    when(req.getServerName()).thenReturn("localhost");
    when(req.getServerPort()).thenReturn(port);
    when(req.getContextPath()).thenReturn(path);

    // when
    final var result = instance.getRedirectURI(req, TasklistURIs.IDENTITY_CALLBACK_URI);

    // then
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> getRedirectUriWhenTasklistIdentityRootUrlProvidedTestData() {
    return Stream.of(
        of("https://localhost", "", "https://localhost/identity-callback"),
        of(
            "http://localhost:8123",
            "/test-path",
            "http://localhost:8123/test-path/identity-callback"));
  }

  @ParameterizedTest
  @MethodSource("getRedirectUriWhenTasklistIdentityRootUrlProvidedTestData")
  void getRedirectUriWhenTasklistIdentityRootUrlProvided(
      String identityRedirectRootUrl, String path, String expected) {
    // given
    final var identityProperties = new IdentityProperties();
    identityProperties.setRedirectRootUrl(identityRedirectRootUrl);
    when(tasklistProperties.getIdentity()).thenReturn(identityProperties);

    final var req = mock(HttpServletRequest.class);
    when(req.getContextPath()).thenReturn(path);

    // when
    final var result = instance.getRedirectURI(req, TasklistURIs.IDENTITY_CALLBACK_URI);

    // then
    assertThat(result).isEqualTo(expected);
    verify(req, never()).getScheme();
    verify(req, never()).getServerName();
    verify(req, never()).getServerPort();
  }
}
