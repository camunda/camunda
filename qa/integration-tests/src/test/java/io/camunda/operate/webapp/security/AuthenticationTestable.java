/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import org.assertj.core.util.Lists;
import io.camunda.operate.webapp.rest.dto.UserDto;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.USER_ENDPOINT;
import static io.camunda.operate.webapp.security.OperateURIs.*;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

public interface AuthenticationTestable {

  String SET_COOKIE_HEADER = "Set-Cookie";

  // Frontend is relying on this - see e2e tests
  Pattern COOKIE_PATTERN = Pattern.compile("^" + OperateURIs.COOKIE_JSESSIONID + "=[0-9A-Z]{32}$", Pattern.CASE_INSENSITIVE);
  String CURRENT_USER_URL = AUTHENTICATION_URL + USER_ENDPOINT;

  default HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    HttpHeaders headers = new HttpHeaders();
    if(responseHeaders.containsKey(X_CSRF_HEADER)) {
      String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader,csrfToken);
    }
    return headers;
  }

  default HttpEntity<Map<String, String>> prepareRequestWithCookies(ResponseEntity<?> response) {
    HttpHeaders headers = getHeaderWithCSRF(response.getHeaders());
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getCookie(response).orElse(""));

    Map<String, String> body = new HashMap<>();
    return new HttpEntity<>(body, headers);
  }

  default List<String> getCookies(ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get(SET_COOKIE_HEADER)).orElse(Lists.emptyList());
  }

  default Optional<String> getCookie(ResponseEntity<?> response) {
    return getCookies(response).stream().filter(key -> key.contains(COOKIE_JSESSIONID)).findFirst();
  }

  default List<String> getCSRFTokens(ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get(SET_COOKIE_HEADER)).orElse(Lists.emptyList());
  }

  default void assertThatCookiesAreSet(ResponseEntity<?> response,boolean csrfEnabled) {
    Optional<String> cookie = getCookie(response);
    assertThat(cookie).isPresent();
    assertThat(cookie.get()).contains(COOKIE_JSESSIONID);
    assertThat(cookie.get().split(";")[0]).matches(COOKIE_PATTERN);
    if(csrfEnabled) {
      List<String> csrfTokens = getCSRFTokens(response);
      assertThat(csrfTokens).isNotEmpty();
      assertThat(csrfTokens.get(0)).isNotEmpty();
    }
  }

  default void assertThatCookiesAreDeleted(ResponseEntity<?> response, boolean csrfEnabled) {
    final String emptyValue = "=;";
    Optional<String> cookie = getCookie(response);
    cookie.ifPresent(s -> assertThat(s).contains(COOKIE_JSESSIONID + emptyValue));
    if (csrfEnabled) {
      List<String> crsfTokens = getCSRFTokens(response);
      if(!crsfTokens.isEmpty()) {
        // The same cookie will be set sometimes 2 times: the second (last) one should be empty
        assertThat(crsfTokens.get(crsfTokens.size() - 1)).contains(X_CSRF_TOKEN + emptyValue);
      }
    }
  }

  default ResponseEntity<Void> login(String username,String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return getTestRestTemplate().postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  default ResponseEntity<?> logout(ResponseEntity<?> previousResponse) {
    HttpEntity<Map<String, String>> request = prepareRequestWithCookies(previousResponse);
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default UserDto getCurrentUser(ResponseEntity<?> previousResponse) {
    final ResponseEntity<UserDto> responseEntity = getTestRestTemplate().exchange(CURRENT_USER_URL, HttpMethod.GET,
        prepareRequestWithCookies(previousResponse), UserDto.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    return responseEntity.getBody();
  }

  public TestRestTemplate getTestRestTemplate();
}
