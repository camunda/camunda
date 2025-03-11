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

import io.camunda.config.operate.WebSecurityProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  default HttpHeaders getHeaderWithCSRF(final HttpHeaders responseHeaders) {
    final HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(X_CSRF_TOKEN)) {
      final String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(X_CSRF_TOKEN, csrfToken);
    }
    return headers;
  }

  default HttpEntity<Map<String, String>> prepareRequestWithCookies(
      final ResponseEntity<?> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getSessionCookies(response).stream().findFirst().orElse(""));

    final Map<String, String> body = new HashMap<>();
    return new HttpEntity<>(body, headers);
  }

  default List<String> getCookies(final ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get(SET_COOKIE_HEADER)).orElse(List.of());
  }

  default List<String> getSessionCookies(final ResponseEntity<?> response) {
    return getCookies(response).stream()
        .filter(key -> key.contains(COOKIE_JSESSIONID))
        .collect(Collectors.toList());
  }

  default void assertThatCookiesAndSecurityHeadersAreSet(final ResponseEntity<?> response) {
    final List<String> cookies = getSessionCookies(response);
    assertThat(cookies).isNotEmpty();
    final String lastSetCookie = cookies.get(cookies.size() - 1);
    assertThat(lastSetCookie.split(";")[0]).matches(COOKIE_PATTERN);
    assertSameSiteIsSet(lastSetCookie);
    assertThatSecurityHeadersAreSet(response);
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
    final String emptyValue = "=;";
    final List<String> sessionCookies = getSessionCookies(response);
    if (!sessionCookies.isEmpty()) {
      final String lastSetCookie = sessionCookies.get(sessionCookies.size() - 1);
      assertThat(lastSetCookie).contains(COOKIE_JSESSIONID + emptyValue);
    }
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
