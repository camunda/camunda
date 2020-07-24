/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.sso;

import static io.zeebe.tasklist.util.CollectionUtil.asMap;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.X_CSRF_HEADER;
import static io.zeebe.tasklist.webapp.security.WebSecurityConfig.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.zeebe.tasklist.util.TasklistIntegrationTest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    properties = {
      "zeebe.tasklist.auth0.clientId=1",
      "zeebe.tasklist.auth0.clientSecret=2",
      "zeebe.tasklist.auth0.organization=3",
      "zeebe.tasklist.auth0.domain=domain",
      "zeebe.tasklist.auth0.backendDomain=backendDomain",
      "zeebe.tasklist.auth0.claimName=claimName"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"sso-auth", "test"})
public class AuthenticationTest extends TasklistIntegrationTest {

  private static final String CURRENT_USER_QUERY =
      "{currentUser{ username \n lastname \n firstname }}";

  private static final String COOKIE_KEY = "Cookie";

  private static final String TASKLIST_TESTUSER = "tasklist-testuser";

  @LocalServerPort int randomServerPort;

  @Autowired TestRestTemplate testRestTemplate;

  @Autowired SSOWebSecurityConfig ssoConfig;

  @MockBean AuthenticationController authenticationController;

  @Autowired private ObjectMapper objectMapper;

  @Before
  public void setUp() throws Throwable {
    // mock building authorizeUrl
    final AuthorizeUrl mockedAuthorizedUrl = mock(AuthorizeUrl.class);
    given(authenticationController.buildAuthorizeUrl(isNotNull(), isNotNull(), isNotNull()))
        .willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withAudience(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withScope(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.build())
        .willReturn(
            "https://domain/authorize?redirect_uri=http://localhost:58117/sso-callback&client_id=1&audience=https://backendDomain/userinfo");
  }

  @Test
  public void testLoginSuccess() throws Exception {
    final HttpEntity<?> cookies = loginWithSSO();
    final ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            ssoConfig.getDomain(),
            SSOWebSecurityConfig.CALLBACK_URI,
            ssoConfig.getClientId(),
            ssoConfig.getBackendDomain());
    // Step 3 Call back uri with invalid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(tokensFrom(ssoConfig.getClaimName(), "wrong-organization"));

    response = get(SSOWebSecurityConfig.CALLBACK_URI, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            ssoConfig.getDomain(),
            "logout",
            ssoConfig.getClientId(),
            SSOWebSecurityConfig.NO_PERMISSION);

    response = get(SSOWebSecurityConfig.ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithOtherException() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            ssoConfig.getDomain(),
            SSOWebSecurityConfig.CALLBACK_URI,
            ssoConfig.getClientId(),
            ssoConfig.getBackendDomain());
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class)
        .when(authenticationController)
        .handle(any(), any());

    response = get(SSOWebSecurityConfig.CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.NO_PERMISSION));
  }

  @Test
  public void testLogout() throws Throwable {
    // Step 1 Login
    final HttpEntity<?> cookies = loginWithSSO();
    // Step 3 Now we should have access to root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Step 2 logout
    response = get(SSOWebSecurityConfig.LOGOUT_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            ssoConfig.getDomain(),
            "logout",
            ssoConfig.getClientId(),
            urlFor(SSOWebSecurityConfig.ROOT));
    // Redirected to Login
    response = get(SSOWebSecurityConfig.ROOT);
    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
  }

  @Test
  public void testLoginToAPIResource() throws Exception {
    // Step 1: try to access current user
    ResponseEntity<String> response = getCurrentUserByGraphQL(new HttpEntity<>(new HttpHeaders()));
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));

    // Step 2: Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            ssoConfig.getDomain(),
            SSOWebSecurityConfig.CALLBACK_URI,
            ssoConfig.getClientId(),
            ssoConfig.getBackendDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(tokensFrom(ssoConfig.getClaimName(), ssoConfig.getOrganization()));

    response = get(SSOWebSecurityConfig.CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.GRAPHQL_URL));

    // when
    final ResponseEntity<String> responseEntity = getCurrentUserByGraphQL(cookies);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    final GraphQLResponse graphQLResponse = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(graphQLResponse.get("$.data.currentUser.username")).isEqualTo(TASKLIST_TESTUSER);
    assertThat(graphQLResponse.get("$.data.currentUser.firstname")).isEqualTo("");
    assertThat(graphQLResponse.get("$.data.currentUser.lastname")).isEqualTo(TASKLIST_TESTUSER);
  }

  private ResponseEntity<String> getCurrentUserByGraphQL(final HttpEntity<?> cookies) {
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            SSOWebSecurityConfig.GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(cookies.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    return responseEntity;
  }

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(SSOWebSecurityConfig.NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Tasklist");
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  protected String redirectLocationIn(ResponseEntity<?> response) {
    return response.getHeaders().getLocation().toString();
  }

  protected ResponseEntity<String> get(String path) {
    return testRestTemplate.getForEntity(path, String.class, new HashMap<String, String>());
  }

  protected ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  protected String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  protected Tokens tokensFrom(String claim, String organization) {
    final String emptyJSONEncoded = toEncodedToken(Collections.emptyMap());
    final long expiresInSeconds = System.currentTimeMillis() / 1000 + 10000; // now + 10 seconds
    final String accountData =
        toEncodedToken(
            asMap(
                claim,
                Arrays.asList(organization),
                "exp",
                expiresInSeconds,
                "name",
                TASKLIST_TESTUSER));
    return new Tokens(
        "accessToken",
        emptyJSONEncoded + "." + accountData + "." + emptyJSONEncoded,
        "refreshToken",
        "type",
        5L);
  }

  protected String toEncodedToken(Map<String, ?> map) {
    return toBase64(toJSON(map));
  }

  protected String toBase64(String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  protected String toJSON(Map<String, ?> map) {
    return new JSONObject(map).toString();
  }

  private HttpEntity<?> loginWithSSO() throws IdentityVerificationException {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            ssoConfig.getDomain(),
            SSOWebSecurityConfig.CALLBACK_URI,
            ssoConfig.getClientId(),
            ssoConfig.getBackendDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(tokensFrom(ssoConfig.getClaimName(), ssoConfig.getOrganization()));

    get(SSOWebSecurityConfig.CALLBACK_URI, cookies);
    return cookies;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    if (response.getHeaders().containsKey("Set-Cookie")) {
      headers.add(COOKIE_KEY, response.getHeaders().get("Set-Cookie").get(0));
    }
    final HttpEntity<?> httpEntity = new HttpEntity<>(new HashMap<>(), headers);
    return httpEntity;
  }

  private HttpEntity<Map<String, ?>> prepareRequestWithCookies(
      HttpHeaders httpHeaders, String graphQlQuery) {

    final HttpHeaders headers = getHeaderWithCSRF(httpHeaders);
    headers.setContentType(APPLICATION_JSON);
    if (httpHeaders.containsKey(COOKIE_KEY)) {
      headers.add(COOKIE_KEY, httpHeaders.get(COOKIE_KEY).get(0));
    }

    final HashMap<String, String> body = new HashMap<>();
    if (graphQlQuery != null) {
      body.put("query", graphQlQuery);
    }

    return new HttpEntity<>(body, headers);
  }

  private HttpHeaders getHeaderWithCSRF(HttpHeaders responseHeaders) {
    final HttpHeaders headers = new HttpHeaders();
    if (responseHeaders.containsKey(X_CSRF_HEADER)) {
      final String csrfHeader = responseHeaders.get(X_CSRF_HEADER).get(0);
      final String csrfToken = responseHeaders.get(X_CSRF_TOKEN).get(0);
      headers.set(csrfHeader, csrfToken);
    }
    return headers;
  }
}
