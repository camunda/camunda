/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_SERVICE_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CookieConfiguration;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

@ExtendWith(MockitoExtension.class)
public class AuthCookieServiceTest {

  @Mock ConfigurationService configurationService;

  @Mock OAuth2AccessToken oAuth2AccessToken;

  @Test
  public void getTokenExceptionFromHttpServletRequest() {
    final HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
    when(requestMock.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    when(requestMock.getCookies()).thenReturn(new Cookie[0]);
    assertThat(AuthCookieService.getAuthCookieToken(requestMock)).isEmpty();
  }

  @Test
  public void getTokenFromHttpServletRequest() {
    // given
    final String authorizationHeader = String.format("Bearer %s", "test");
    final Cookie[] cookies =
        new Cookie[] {
          new Cookie(AuthCookieService.getAuthorizationCookieNameWithSuffix(0), authorizationHeader)
        };
    final HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    when(servletRequestMock.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    when(servletRequestMock.getCookies()).thenReturn(cookies);

    // when
    final Optional<String> token = AuthCookieService.getAuthCookieToken(servletRequestMock);

    // then
    assertThat(token).isPresent().get().isEqualTo("test");
  }

  @ParameterizedTest
  @MethodSource("tokenAndExpectedCookieValues")
  public void createAuthorizationTokenCookies(
      final String tokenValue, final List<String> expectedCookieValues) {
    // given
    final CookieConfiguration cookieConfig = new CookieConfiguration();
    cookieConfig.setMaxSize(2);
    final CloudAuthConfiguration cloudAuthConfig = new CloudAuthConfiguration();
    cloudAuthConfig.setClientId("clusterId");
    final AuthConfiguration authConfig = new AuthConfiguration();
    authConfig.setCookieConfiguration(cookieConfig);
    when(configurationService.getAuthConfiguration()).thenReturn(authConfig);
    authConfig.setCloudAuthConfiguration(cloudAuthConfig);
    when(configurationService.getAuthConfiguration()).thenReturn(authConfig);
    final AuthCookieService authCookieService = new AuthCookieService(configurationService);

    // when
    final List<Cookie> cookies =
        authCookieService.createOptimizeAuthCookies(tokenValue, Instant.now(), "http");

    // then the correct cookies are created
    assertThat(cookies)
        .extracting(Cookie::getValue)
        .containsExactlyElementsOf(expectedCookieValues);
  }

  @ParameterizedTest
  @MethodSource("tokenAndExpectedCookieValues")
  public void extractAuthorizationTokenFromCookies(
      final String expectedTokenValue, final List<String> cookieValues) {
    // given
    final int numberOfCookies = cookieValues.size();
    final HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);

    final List<String> attributeList = new ArrayList<>();
    for (int i = 0; i < numberOfCookies; i++) {
      attributeList.add(AuthCookieService.getAuthorizationCookieNameWithSuffix(i));
      when(servletRequestMock.getAttribute(
              AuthCookieService.getAuthorizationCookieNameWithSuffix(i)))
          .thenReturn(cookieValues.get(i));
    }
    when(servletRequestMock.getAttributeNames()).thenReturn(Collections.enumeration(attributeList));

    // when
    final Optional<String> serviceAccessToken =
        AuthCookieService.getAuthCookieToken(servletRequestMock);

    // then the correct authorization token value can be extracted
    assertThat(serviceAccessToken).isPresent().get().isEqualTo(expectedTokenValue);
  }

  @ParameterizedTest
  @MethodSource("tokenAndExpectedCookieValues")
  public void createServiceTokenCookies(
      final String serviceTokenValue, final List<String> expectedCookieValues) {
    // given
    final CookieConfiguration cookieConfig = new CookieConfiguration();
    cookieConfig.setMaxSize(2);
    final CloudAuthConfiguration cloudAuthConfig = new CloudAuthConfiguration();
    cloudAuthConfig.setClientId("clusterId");
    final AuthConfiguration authConfig = new AuthConfiguration();
    authConfig.setCookieConfiguration(cookieConfig);
    authConfig.setCloudAuthConfiguration(cloudAuthConfig);
    when(configurationService.getAuthConfiguration()).thenReturn(authConfig);
    when(oAuth2AccessToken.getTokenValue()).thenReturn(serviceTokenValue);
    final AuthCookieService authCookieService = new AuthCookieService(configurationService);

    // when
    final List<Cookie> cookies =
        authCookieService.createOptimizeServiceTokenCookies(
            oAuth2AccessToken, Instant.now(), "http");

    // then the correct cookies are created
    assertThat(cookies)
        .extracting(Cookie::getValue)
        .containsExactlyElementsOf(expectedCookieValues);
  }

  @ParameterizedTest
  @MethodSource("tokenAndExpectedCookieValues")
  public void extractServiceTokenFromCookies(
      final String expectedServiceTokenValue, final List<String> cookieValues) {
    // given
    final List<Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < cookieValues.size(); i++) {
      cookies.add(
          new jakarta.servlet.http.Cookie(OPTIMIZE_SERVICE_TOKEN + "_" + i, cookieValues.get(i)));
    }
    final HttpServletRequest servletRequestMock = Mockito.mock(HttpServletRequest.class);
    when(servletRequestMock.getCookies()).thenReturn(cookies.toArray(Cookie[]::new));

    // when
    final Optional<String> serviceAccessToken =
        AuthCookieService.getServiceAccessToken(servletRequestMock);

    // then the correct service token value can be extracted
    assertThat(serviceAccessToken).isPresent().get().isEqualTo(expectedServiceTokenValue);
  }

  private static Stream<Arguments> tokenAndExpectedCookieValues() {
    return Stream.of(
        Arguments.of("a", List.of("a")),
        Arguments.of("bc", List.of("bc")),
        Arguments.of("def", List.of("de", "f")),
        Arguments.of("ghij", List.of("gh", "ij")));
  }
}
