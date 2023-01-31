/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  String CURRENT_USER_QUERY =
      "{currentUser{ userId \n displayName roles salesPlanType c8Links { name link } }}";

  TestRestTemplate getTestRestTemplate();

  default HttpEntity<Map<String, ?>> prepareRequestWithCookies(HttpHeaders httpHeaders) {
    return prepareRequestWithCookies(httpHeaders, null);
  }

  default HttpEntity<Map<String, ?>> prepareRequestWithCookies(
      HttpHeaders httpHeaders, String graphQlQuery) {
    final HttpHeaders headers = new HttpHeaders();
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

  default ResponseEntity<String> logout(ResponseEntity<?> response) {
    final HttpEntity<Map<String, ?>> request = prepareRequestWithCookies(response.getHeaders());
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default ResponseEntity<String> getCurrentUserByGraphQL(final HttpEntity<?> cookies) {
    return getTestRestTemplate()
        .exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(cookies.getHeaders(), CURRENT_USER_QUERY),
            String.class);
  }

  default void assertThatCookiesAreSet(ResponseEntity<?> response) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final String sessionCookie = getSessionCookie(headers).orElse("");
    assertThat(sessionCookie).contains(COOKIE_JSESSIONID);
    assertThat(sessionCookie).contains("SameSite=Lax");
    assertThat(headers).containsKey(CONTENT_SECURITY_POLICY_HEADER);
    assertThat(headers.get(CONTENT_SECURITY_POLICY_HEADER)).isNotNull().isNotEmpty();
  }

  default void assertThatCookiesAreDeleted(ResponseEntity<?> response) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    assertThat(cookies).anyMatch((cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }

  default ResponseEntity<String> get(String path) {
    return getTestRestTemplate().getForEntity(path, String.class);
  }

  default String getCookiesAsString(HttpHeaders headers) {
    return getSessionCookie(headers).orElse("");
  }

  default Optional<String> getSessionCookie(HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(COOKIE_JSESSIONID)).findFirst();
  }

  default List<String> getCookies(HttpHeaders headers) {
    return Optional.ofNullable(headers.get(SET_COOKIE_HEADER)).orElse(List.of());
  }

  default String redirectLocationIn(ResponseEntity<?> response) {
    final URI uri = response.getHeaders().getLocation();
    return uri == null ? "" : uri.toString();
  }
}
