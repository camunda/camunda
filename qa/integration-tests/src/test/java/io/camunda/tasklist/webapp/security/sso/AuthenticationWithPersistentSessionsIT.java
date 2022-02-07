/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;
import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_CALLBACK;
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
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.sso.AuthSSOApplication;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
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
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

@RunWith(Parameterized.class)
@SpringBootTest(
    classes = {
      AuthSSOApplication.class,
    },
    properties = {
      "camunda.tasklist.auth0.clientId=1",
      "camunda.tasklist.auth0.clientSecret=2",
      "camunda.tasklist.auth0.organization=3",
      "camunda.tasklist.auth0.domain=domain",
      "camunda.tasklist.auth0.backendDomain=backendDomain",
      "camunda.tasklist.auth0.claimName=claimName",
      "camunda.tasklist.persistentSessionsEnabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({SSO_AUTH_PROFILE, "test"})
public class AuthenticationWithPersistentSessionsIT implements AuthenticationTestable {

  @ClassRule public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

  private static final String COOKIE_KEY = "Cookie";
  private static final String TASKLIST_TESTUSER = "tasklist-testuser";
  private static final String TASKLIST_TESTUSER_EMAIL = "testuser@tasklist.io";
  @Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();

  @LocalServerPort int randomServerPort;

  @Autowired TestRestTemplate testRestTemplate;

  @Autowired TasklistProperties tasklistProperties;

  @MockBean AuthenticationController authenticationController;

  @Autowired private ObjectMapper objectMapper;
  private final BiFunction<String, String, Tokens> orgExtractor;

  public AuthenticationWithPersistentSessionsIT(BiFunction<String, String, Tokens> orgExtractor) {
    this.orgExtractor = orgExtractor;
  }

  @Parameters
  public static Collection<BiFunction<String, String, Tokens>> orgExtractors() {
    return List.of(AuthenticationWithPersistentSessionsIT::tokensWithOrgAsMapFrom);
  }

  @Before
  public void setUp() {
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
    final ResponseEntity<String> response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getBackendDomain());
    // Step 3 Call back uri with invalid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(tasklistProperties.getAuth0().getClaimName(), "wrong-organization"));

    response = get(SSO_CALLBACK, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            "logout",
            tasklistProperties.getAuth0().getClientId(),
            NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithOtherException() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getBackendDomain());
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class)
        .when(authenticationController)
        .handle(any(), any());

    response = get(SSO_CALLBACK, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(NO_PERMISSION));
  }

  @Test
  public void testLogout() throws Throwable {
    // Step 1 Login
    final HttpEntity<?> cookies = loginWithSSO();
    // Step 3 Now we should have access to root
    ResponseEntity<String> response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Step 2 logout
    response = get(LOGOUT_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            "logout",
            tasklistProperties.getAuth0().getClientId(),
            urlFor(ROOT));
    // Redirected to Login
    response = get(ROOT);
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginToAPIResource() throws Exception {
    // Step 1: try to access current user
    ResponseEntity<String> response = getCurrentUserByGraphQL(new HttpEntity<>(new HttpHeaders()));
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2: Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getBackendDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                tasklistProperties.getAuth0().getClaimName(),
                tasklistProperties.getAuth0().getOrganization()));

    response = get(SSO_CALLBACK, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(GRAPHQL_URL));

    // when
    final ResponseEntity<String> responseEntity = getCurrentUserByGraphQL(cookies);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    final GraphQLResponse graphQLResponse = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(graphQLResponse.get("$.data.currentUser.userId")).isEqualTo(TASKLIST_TESTUSER_EMAIL);
    assertThat(graphQLResponse.get("$.data.currentUser.displayName")).isEqualTo(TASKLIST_TESTUSER);
  }

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Tasklist");
  }

  private void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  private String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private static Tokens tokensWithOrgAsMapFrom(String claim, String organization) {
    final String emptyJSONEncoded = toEncodedToken(Map.of());
    final long expiresInSeconds = System.currentTimeMillis() / 1000 + 10000; // now + 10 seconds
    final Map<String, Object> orgMap =
        Map.of("id", organization, "roles", List.of("owner", "user"));
    final String accountData =
        toEncodedToken(
            asMap(
                claim,
                List.of(orgMap),
                "exp",
                expiresInSeconds,
                "name",
                TASKLIST_TESTUSER,
                "email",
                TASKLIST_TESTUSER_EMAIL));
    return new Tokens(
        "accessToken",
        emptyJSONEncoded + "." + accountData + "." + emptyJSONEncoded,
        "refreshToken",
        "type",
        5L);
  }

  private static String toEncodedToken(Map<String, ?> map) {
    return toBase64(toJSON(map));
  }

  private static String toBase64(String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  private static String toJSON(Map<String, ?> map) {
    return new JSONObject(map).toString();
  }

  private HttpEntity<?> loginWithSSO() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getBackendDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                tasklistProperties.getAuth0().getClaimName(),
                tasklistProperties.getAuth0().getOrganization()));

    get(SSO_CALLBACK, cookies);
    // Sometimes the testLogin fails without waiting
    TimeUnit.MILLISECONDS.sleep(1000);
    return cookies;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    if (response.getHeaders().containsKey("Set-Cookie")) {
      headers.add(COOKIE_KEY, response.getHeaders().get("Set-Cookie").get(0));
    }
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  public HttpEntity<Map<String, ?>> prepareRequestWithCookies(
      HttpHeaders httpHeaders, String graphQlQuery) {

    final HttpHeaders headers = new HttpHeaders();
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
}
