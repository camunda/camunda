/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.USER_ENDPOINT;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.operate.property.WebSecurityProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.server.header.ContentSecurityPolicyServerHttpHeadersWriter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public interface AuthenticationTestable {

  String SET_COOKIE_HEADER = "Set-Cookie";
  // Frontend is relying on this - see e2e tests
  Pattern COOKIE_PATTERN =
      Pattern.compile(
          "^" + OperateURIs.COOKIE_JSESSIONID + "=[0-9A-Z]{32}$", Pattern.CASE_INSENSITIVE);
  String CURRENT_USER_URL = AUTHENTICATION_URL + USER_ENDPOINT;
  String COOKIE_ENTRY_SEPARATOR = ";";
  String EMPTY_HEADER_VALUE = "=" + COOKIE_ENTRY_SEPARATOR;

  default HttpHeaders getHeaderWithCSRF(final HttpHeaders responseHeaders) {
    final HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(X_CSRF_TOKEN)) {
      headers.set(
          X_CSRF_TOKEN, Objects.requireNonNull(responseHeaders.get(X_CSRF_TOKEN)).getFirst());
    }
    return headers;
  }

  default HttpEntity<Map<String, String>> prepareRequestWithCookies(
      final ResponseEntity<?> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    getSessionCookie(response).ifPresent(sessionCookie -> headers.add("Cookie", sessionCookie));
    getCsrfCookie(response).ifPresent(csrfCookie -> headers.add(X_CSRF_TOKEN, csrfCookie));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  default List<String> getCookies(final ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get(SET_COOKIE_HEADER)).orElse(List.of());
  }

  default Optional<String> getCsrfCookie(final ResponseEntity<?> response) {
    return getCookies(response).stream().filter(key -> key.startsWith(X_CSRF_TOKEN)).findFirst();
  }

  default Optional<String> getSessionCookie(final ResponseEntity<?> response) {
    return getCookies(response).stream()
        .filter(key -> key.startsWith(COOKIE_JSESSIONID))
        .findFirst();
  }

  default void assertThatCookiesAndSecurityHeadersAreSet(
      final ResponseEntity<?> response, final boolean csrfEnabled) {
    getSessionCookie(response)
        .ifPresent(
            sessionCookie -> {
              assertThat(sessionCookie.split(COOKIE_ENTRY_SEPARATOR)[0]).matches(COOKIE_PATTERN);
              assertSameSiteIsSet(sessionCookie);
            });
    if (csrfEnabled) {
      getCsrfCookie(response)
          .ifPresent(
              csrfCookie -> {
                assertThat(csrfCookie).contains(X_CSRF_TOKEN);
                assertThat(csrfCookie.split(COOKIE_ENTRY_SEPARATOR)[0]).isNotEmpty();
              });
      assertThatCSRFHeadersAreSet(response);
    }
    assertThatSecurityHeadersAreSet(response);
  }

  default void assertThatCSRFHeadersAreSet(final ResponseEntity<?> response) {
    final var csrfHeader = response.getHeaders().get(X_CSRF_TOKEN);
    assertThat(csrfHeader).isNotEmpty();
    final var token = csrfHeader.getFirst();
    assertThat(token).isNotEmpty();
  }

  default void assertThatSecurityHeadersAreSet(final ResponseEntity<?> response) {
    final var cspHeaderValues =
        response
            .getHeaders()
            .getOrEmpty(ContentSecurityPolicyServerHttpHeadersWriter.CONTENT_SECURITY_POLICY);
    assertThat(cspHeaderValues).isNotEmpty();
    assertThat(cspHeaderValues)
        .first()
        .isIn(
            WebSecurityProperties.DEFAULT_SM_SECURITY_POLICY,
            WebSecurityProperties.DEFAULT_SAAS_SECURITY_POLICY);
  }

  default void assertSameSiteIsSet(final String cookie) {
    assertThat(cookie).contains("SameSite=Lax");
  }

  default void assertThatCookiesAreDeleted(final ResponseEntity<?> response) {
    getSessionCookie(response)
        .ifPresent(s -> assertThat(s).contains(COOKIE_JSESSIONID + EMPTY_HEADER_VALUE));
  }

  default ResponseEntity<Void> login(final String username, final String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return getTestRestTemplate()
        .postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  default ResponseEntity<?> logout(final ResponseEntity<?> previousResponse) {
    final HttpEntity<Map<String, String>> request = prepareRequestWithCookies(previousResponse);
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default UserDto getCurrentUser(final ResponseEntity<?> previousResponse) {
    final ResponseEntity<UserDto> responseEntity =
        getTestRestTemplate()
            .exchange(
                CURRENT_USER_URL,
                HttpMethod.GET,
                prepareRequestWithCookies(previousResponse),
                UserDto.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    return responseEntity.getBody();
  }

  default ResponseEntity<String> get(final String path) {
    return getTestRestTemplate().getForEntity(path, String.class);
  }

  default String redirectLocationIn(final ResponseEntity<?> response) {
    final URI location = response.getHeaders().getLocation();
    if (location != null) {
      return location.toString();
    }
    return null;
  }

  TestRestTemplate getTestRestTemplate();
}
