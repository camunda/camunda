/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.X_CSRF_HEADER;
import static io.camunda.tasklist.webapp.security.TasklistURIs.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public interface AuthenticationTestable {

  String SET_COOKIE_HEADER = "Set-Cookie";

  TestRestTemplate getTestRestTemplate();

  default HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    final HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(TasklistURIs.X_CSRF_HEADER)) {
      final String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      final String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader, csrfToken);
    }
    return headers;
  }

  default HttpEntity<Map> prepareRequestWithCookies(HttpHeaders httpHeaders) {
    return prepareRequestWithCookies(httpHeaders, null);
  }

  default HttpEntity<Map> prepareRequestWithCookies(HttpHeaders httpHeaders, String graphQlQuery) {

    final HttpHeaders headers = getHeaderWithCSRF(httpHeaders);
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getCookiesAsString(httpHeaders));

    final HashMap<String, String> body = new HashMap<>();
    if (graphQlQuery != null) {
      body.put("query", graphQlQuery);
    }

    return new HttpEntity<>(body, headers);
  }

  default ResponseEntity<Void> login(String username, String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return getTestRestTemplate()
        .postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  default ResponseEntity<String> logout(ResponseEntity<Void> response) {
    final HttpEntity<Map> request = prepareRequestWithCookies(response.getHeaders());
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default void assertThatCookiesAreSet(ResponseEntity<?> response, boolean csrfEnabled) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    assertThat(getSessionCookie(headers).orElse("")).contains(COOKIE_JSESSIONID);
    if (csrfEnabled) {
      assertThat(headers).containsKey(X_CSRF_TOKEN);
      assertThat(headers.get(X_CSRF_TOKEN).get(0)).isNotBlank();
    }
  }

  default void assertThatCookiesAreDeleted(ResponseEntity<?> response, boolean csrfEnabled) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    if (csrfEnabled) {
      assertThat(cookies).anyMatch((cookie) -> cookie.contains(X_CSRF_TOKEN + emptyValue));
    }
    assertThat(cookies).anyMatch((cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }

  default String getCookiesAsString(HttpHeaders headers) {
    return String.format(
        "%s; %s", getSessionCookie(headers).orElse(""), getCSRFCookie(headers).orElse(""));
  }

  default Optional<String> getSessionCookie(HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(COOKIE_JSESSIONID)).findFirst();
  }

  default Optional<String> getCSRFCookie(HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(X_CSRF_TOKEN)).findFirst();
  }

  default List<String> getCookies(HttpHeaders headers) {
    return Optional.ofNullable(headers.get(SET_COOKIE_HEADER)).orElse(Lists.emptyList());
  }
}
