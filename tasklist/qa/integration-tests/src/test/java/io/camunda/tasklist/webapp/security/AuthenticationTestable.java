/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.USERS_URL_V1;
import static io.camunda.tasklist.webapp.security.TasklistURIs.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.tasklist.webapp.dto.UserDTO;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public interface AuthenticationTestable {

  String SET_COOKIE_HEADER = "Set-Cookie";

  String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

  TestRestTemplate getTestRestTemplate();

  default HttpHeaders getHeaderWithCSRF(final HttpHeaders responseHeaders) {
    final HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(X_CSRF_TOKEN)) {
      final String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(X_CSRF_TOKEN, csrfToken);
    }
    return headers;
  }

  default List<String> getCSRFCookies(final ResponseEntity<?> response) {
    return getCookies(response.getHeaders()).stream()
        .filter(key -> key.contains(X_CSRF_TOKEN))
        .collect(Collectors.toList());
  }

  default HttpEntity<Map<String, ?>> prepareRequestWithCookies(final HttpHeaders httpHeaders) {
    return prepareRequestWithCookies(httpHeaders, null);
  }

  default HttpEntity<Map<String, ?>> prepareRequestWithCookies(
      final HttpHeaders httpHeaders, final String graphQlQuery) {
    final HttpHeaders headers = getHeaderWithCSRF(httpHeaders);
    headers.setContentType(APPLICATION_JSON);

    // Extract and set cookies
    final List<String> cookieList = httpHeaders.get(HttpHeaders.SET_COOKIE);
    if (cookieList != null) {
      final String cookies = String.join("; ", cookieList);
      headers.set(HttpHeaders.COOKIE, cookies);
    }

    final HashMap<String, String> body = new HashMap<>();
    if (graphQlQuery != null) {
      body.put("query", graphQlQuery);
    }

    return new HttpEntity<>(body, headers);
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

  default ResponseEntity<String> logout(final ResponseEntity<?> response) {
    final HttpEntity<Map<String, ?>> request = prepareRequestWithCookies(response.getHeaders());
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default ResponseEntity<UserDTO> getCurrentUserByRestApi(final HttpEntity<?> cookies) {
    return getTestRestTemplate()
        .exchange(
            USERS_URL_V1.concat("/current"),
            HttpMethod.GET,
            prepareRequestWithCookies(cookies.getHeaders(), null),
            UserDTO.class);
  }

  default void assertThatCookiesAreSet(
      final ResponseEntity<?> response, final boolean csrfEnabled) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final String sessionCookie = getSessionCookie(headers).orElse("");
    assertThat(sessionCookie).contains(COOKIE_JSESSIONID);
    assertThat(sessionCookie).contains("SameSite=Lax");
    assertThat(headers).containsKey(CONTENT_SECURITY_POLICY_HEADER);
    if (csrfEnabled) {
      final List<String> csrfTokens = getCSRFCookies(response);
      assertThat(csrfTokens).isNotEmpty();
      final String lastSetCRSFCookie = csrfTokens.get(csrfTokens.size() - 1);
      assertThat(lastSetCRSFCookie).contains(X_CSRF_TOKEN);
      assertThat(lastSetCRSFCookie.split(";")[0]).isNotEmpty();
    }
    assertThat(headers.get(CONTENT_SECURITY_POLICY_HEADER)).isNotNull().isNotEmpty();
  }

  default void assertThatCookiesAreDeleted(final ResponseEntity<?> response) {
    final HttpHeaders headers = response.getHeaders();
    if (headers.containsKey(SET_COOKIE_HEADER)) {
      final List<String> cookies = headers.get(SET_COOKIE_HEADER);
      final String emptyValue = "=;";
      assertThat(cookies).anyMatch((cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
    }
  }

  default ResponseEntity<String> get(final String path) {
    return getTestRestTemplate().getForEntity(path, String.class);
  }

  default String getCookiesAsString(final HttpHeaders headers) {
    return getSessionCookie(headers).orElse("");
  }

  default Optional<String> getSessionCookie(final HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(COOKIE_JSESSIONID)).findFirst();
  }

  default List<String> getCookies(final HttpHeaders headers) {
    return Optional.ofNullable(headers.get(SET_COOKIE_HEADER)).orElse(List.of());
  }

  default String redirectLocationIn(final ResponseEntity<?> response) {
    final URI uri = response.getHeaders().getLocation();
    return uri == null ? "" : uri.toString();
  }
}
