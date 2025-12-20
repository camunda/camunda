/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.property.Auth0Properties.DEFAULT_ORGANIZATIONS_KEY;
import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;
import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_CALLBACK;
import static io.camunda.tasklist.webapp.security.sso.AuthenticationIT.TASKLIST_TEST_ROLES;
import static io.camunda.tasklist.webapp.security.sso.AuthenticationIT.TASKLIST_TEST_SALESPLAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.RestTemplateNoRedirectFollowConfiguration;
import io.camunda.tasklist.util.apps.sso.AuthSSOApplication;
import io.camunda.tasklist.webapp.graphql.entity.C8AppLink;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.sso.model.ClusterInfo;
import io.camunda.tasklist.webapp.security.sso.model.ClusterMetadata;
import jakarta.json.Json;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    classes = {
      AuthSSOApplication.class,
      RestTemplateNoRedirectFollowConfiguration.class,
    },
    properties = {
      "camunda.tasklist.auth0.clientId=1",
      "camunda.tasklist.auth0.clientSecret=2",
      "camunda.tasklist.auth0.organization=3",
      "camunda.tasklist.auth0.domain=domain",
      "camunda.tasklist.auth0.claimName=claimName",
      "camunda.tasklist.cloud.clusterId=test-clusterId",
      "camunda.tasklist.cloud.permissionaudience=audience",
      "camunda.tasklist.cloud.permissionurl=https://permissionurl",
      "camunda.tasklist.persistentSessionsEnabled = true",
      "camunda.tasklist.csrf-prevention-enabled= false",
      "camunda.tasklist.cloud.permissionurl=https://permissionurl",
      "camunda.tasklist.cloud.consoleUrl=https://consoleUrl",
      "camunda.tasklist.importer.startLoadingDataOnStartup = false",
      "camunda.tasklist.archiver.rolloverEnabled = false",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({SSO_AUTH_PROFILE, "tasklist", "test", "standalone"})
public class AuthenticationWithPersistentSessionsIT implements AuthenticationTestable {

  private static final String COOKIE_KEY = "Cookie";
  private static final String TASKLIST_TESTUSER = "tasklist-testuser";
  private static final String TASKLIST_TESTUSER_EMAIL = "testuser@tasklist.io";

  @LocalServerPort private int randomServerPort;
  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private TasklistProperties tasklistProperties;
  @MockBean private AuthenticationController authenticationController;

  @MockBean private AssigneeMigrator assigneeMigrator;
  @MockBean private IdentityAuthorizationService identityAuthorizationService;

  @MockBean
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  public static Collection<BiFunction<String, String, Tokens>> orgExtractors() {
    return List.of(AuthenticationWithPersistentSessionsIT::tokensWithOrgAsMapFrom);
  }

  private static Tokens tokensWithOrgAsMapFrom(final String claim, final String organization) {
    final String emptyJSONEncoded = toEncodedToken(Map.of());
    final long expiresInSeconds = System.currentTimeMillis() / 1000 + 10000; // now + 10 seconds
    final Map<String, Object> orgMap = Map.of("id", organization);
    final String accountData =
        toEncodedToken(
            asMap(
                claim,
                List.of(orgMap),
                "sub",
                TASKLIST_TESTUSER,
                "exp",
                expiresInSeconds,
                "name",
                TASKLIST_TESTUSER,
                "email",
                TASKLIST_TESTUSER_EMAIL,
                DEFAULT_ORGANIZATIONS_KEY,
                List.of(Map.of("id", "3", "roles", List.of("user", "analyst")))));
    return new Tokens(
        "accessToken",
        emptyJSONEncoded + "." + accountData + "." + emptyJSONEncoded,
        "refreshToken",
        "type",
        5L);
  }

  private static String toEncodedToken(final Map<String, Object> map) {
    return toBase64(toJSON(map));
  }

  private static String toBase64(final String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  private static String toJSON(final Map<String, Object> map) {
    return Json.createObjectBuilder(map).build().toString();
  }

  @BeforeEach
  public void setUp() {
    // mock AssigneeMigrator
    doNothing().when(assigneeMigrator).migrateUsageMetrics(any());
    // mock building authorizeUrl
    final AuthorizeUrl mockedAuthorizedUrl = mock(AuthorizeUrl.class);
    given(authenticationController.buildAuthorizeUrl(isNotNull(), isNotNull(), isNotNull()))
        .willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withAudience(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withScope(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.build())
        .willReturn(
            "https://domain/authorize?redirect_uri=http://localhost:58117/sso-callback&client_id=1&audience=https://domain/userinfo");
  }

  @ParameterizedTest
  @MethodSource("orgExtractors")
  public void testLoginSuccess(final BiFunction<String, String, Tokens> orgExtractor)
      throws Exception {
    final HttpEntity<?> cookies = loginWithSSO(orgExtractor);
    final ResponseEntity<String> response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @ParameterizedTest
  @MethodSource("orgExtractors")
  public void testLoginFailedWithNoPermissions(
      final BiFunction<String, String, Tokens> orgExtractor) throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getDomain());
    // Step 3 Call back uri with invalid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(tasklistProperties.getAuth0().getClaimName(), "wrong-organization"));

    response = get(SSO_CALLBACK, cookies);
    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

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
            tasklistProperties.getAuth0().getDomain());
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class)
        .when(authenticationController)
        .handle(any(), any());

    response = get(SSO_CALLBACK, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(NO_PERMISSION));
  }

  @ParameterizedTest
  @MethodSource("orgExtractors")
  public void testLogout(final BiFunction<String, String, Tokens> orgExtractor) throws Throwable {
    // Step 1 Login
    final HttpEntity<?> cookies = loginWithSSO(orgExtractor);
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

  private void assertThatUnauthorizedIsReturned(final ResponseEntity<?> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @ParameterizedTest
  @MethodSource("orgExtractors")
  public void testLoginToAPIResource(final BiFunction<String, String, Tokens> orgExtractor)
      throws Exception {
    // Step 1: try to access current user
    ResponseEntity<String> response = getCurrentUserByGraphQL(new HttpEntity<>(new HttpHeaders()));
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatUnauthorizedIsReturned(response);

    // Step 2: Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                tasklistProperties.getAuth0().getClaimName(),
                tasklistProperties.getAuth0().getOrganization()));

    // Test no ClusterMetadata
    mockEmptyClusterMetadata();

    final HttpEntity<?> loggedCookies = loginWithSSO(orgExtractor);
    ResponseEntity<String> responseEntity = getCurrentUserByGraphQL(loggedCookies);
    GraphQLResponse graphQLResponse = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(graphQLResponse.get("$.data.currentUser.c8Links", List.class)).isEmpty();

    // Test with ClusterMetadata
    mockClusterMetadata();
    // when
    responseEntity = getCurrentUserByGraphQL(loggedCookies);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    graphQLResponse = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(graphQLResponse.get("$.data.currentUser.userId")).isEqualTo(TASKLIST_TESTUSER_EMAIL);
    assertThat(graphQLResponse.get("$.data.currentUser.displayName")).isEqualTo(TASKLIST_TESTUSER);
    assertThat(graphQLResponse.get("$.data.currentUser.salesPlanType"))
        .isEqualTo(TASKLIST_TEST_SALESPLAN.getType());
    assertThat(graphQLResponse.get("$.data.currentUser.roles", List.class))
        .asList()
        .isEqualTo(TASKLIST_TEST_ROLES);
    assertThat(graphQLResponse.getList("$.data.currentUser.c8Links", C8AppLink.class))
        .containsExactly(
            new C8AppLink()
                .setName("console")
                .setLink("https://console.audience/org/3/cluster/test-clusterId"),
            new C8AppLink().setName("operate").setLink("http://operate-url"),
            new C8AppLink().setName("optimize").setLink("http://optimize-url"),
            new C8AppLink().setName("modeler").setLink("https://modeler.audience/org/3"),
            new C8AppLink().setName("tasklist").setLink("http://tasklist-url"),
            new C8AppLink().setName("zeebe").setLink("grpc://zeebe-url"));
  }

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Tasklist");
  }

  private void assertThatRequestIsRedirectedTo(final ResponseEntity<?> response, final String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private ResponseEntity<String> get(final String path, final HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  private String urlFor(final String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private HttpEntity<?> loginWithSSO(final BiFunction<String, String, Tokens> orgExtractor)
      throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userinfo
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

  private HttpEntity<?> httpEntityWithCookie(final ResponseEntity<String> response) {
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

  @Override
  public HttpEntity<Map<String, ?>> prepareRequestWithCookies(
      final HttpHeaders httpHeaders, final String graphQlQuery) {

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

  private void mockPermissionAllowed() {
    final ClusterInfo.OrgPermissions tasklist =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(tasklist, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, TASKLIST_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
            eq("https://permissionurl/3"), eq(HttpMethod.GET), (HttpEntity) any(), (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private void mockClusterMetadata() {
    final ClusterMetadata clusterMetadata =
        new ClusterMetadata()
            .setName("test-cluster")
            .setUuid("test-clusterId")
            .setUrls(
                Map.of(
                    ClusterMetadata.AppName.OPERATE, "http://operate-url",
                    ClusterMetadata.AppName.TASKLIST, "http://tasklist-url",
                    ClusterMetadata.AppName.OPTIMIZE, "http://optimize-url",
                    ClusterMetadata.AppName.ZEEBE, "grpc://zeebe-url"));
    final ClusterMetadata[] clusterMetadatas = new ClusterMetadata[] {clusterMetadata};
    when(restTemplate.exchange(
            eq("https://consoleUrl/external/organizations/3/clusters"),
            eq(HttpMethod.GET),
            (HttpEntity) any(),
            eq(ClusterMetadata[].class)))
        .thenReturn(new ResponseEntity<>(clusterMetadatas, HttpStatus.OK));
  }

  private void mockEmptyClusterMetadata() {
    when(restTemplate.exchange(
            eq("https://consoleUrl/external/organizations/3/clusters"),
            eq(HttpMethod.GET),
            (HttpEntity) any(),
            eq(ClusterMetadata[].class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
  }
}
