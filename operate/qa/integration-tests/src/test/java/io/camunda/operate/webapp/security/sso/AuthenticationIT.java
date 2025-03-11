/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.OperateProfileService.SSO_AUTH_PROFILE;
import static io.camunda.operate.property.Auth0Properties.DEFAULT_ORGANIZATIONS_KEY;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_CALLBACK_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.management.IndicesCheck;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.controllers.OperateIndexController;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.auth.RolePermissionService;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo.SalesPlan;
import io.camunda.operate.webapp.security.sso.model.ClusterMetadata;
import io.camunda.webapps.WebappsModuleConfiguration;
import jakarta.json.Json;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OAuth2WebConfigurer.class,
      Jwt2AuthenticationTokenConverter.class,
      CCSaaSJwtAuthenticationTokenValidator.class,
      SSOWebSecurityConfig.class,
      Auth0Service.class,
      C8ConsoleService.class,
      SSOController.class,
      SSOUserService.class,
      AuthenticationRestService.class,
      RolePermissionService.class,
      OperateURIs.class,
      OperateProperties.class,
      OperateProfileService.class,
      OperateIndexController.class,
      WebappsModuleConfiguration.class,
      CamundaSecurityProperties.class,
    },
    properties = {
      "server.servlet.context-path=" + AuthenticationIT.CONTEXT_PATH,
      "spring.web.resources.add-mappings=true",
      "camunda.operate.auth0.clientId=1",
      "camunda.operate.auth0.clientSecret=2",
      "camunda.operate.cloud.organizationId=3",
      "camunda.operate.cloud.clusterId=test-clusterId",
      "camunda.operate.auth0.domain=domain",
      "camunda.operate.auth0.claimName=claimName",
      "camunda.operate.cloud.permissionaudience=audience",
      "camunda.operate.cloud.permissionurl=https://permissionurl",
      "camunda.operate.cloud.consoleUrl=https://consoleUrl",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({SSO_AUTH_PROFILE, "test"})
public class AuthenticationIT implements AuthenticationTestable {

  public static final SalesPlan OPERATE_TEST_SALESPLAN = new SalesPlan("test");

  public static final String CONTEXT_PATH = "/operate-test";
  @ClassRule public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();
  private final BiFunction<String, String, Tokens> orgExtractor =
      AuthenticationIT::tokensWithOrgAsMapFrom;
  @LocalServerPort private int randomServerPort;
  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private OperateProperties operateProperties;
  @MockBean private AuthenticationController authenticationController;
  @SpyBean private Auth0Service auth0Service;
  @Autowired private BeanFactory beanFactory;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ApplicationContext applicationContext;

  @MockBean
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  @MockBean private IndicesCheck probes;

  private static Tokens tokensWithOrgAsMapFrom(final String claim, final String organization) {
    final String emptyJSONEncoded = toEncodedToken(Collections.EMPTY_MAP);
    final long expiresInSeconds = System.currentTimeMillis() / 1000 + 10000; // now + 10 seconds
    final Map<String, Object> orgMap = Map.of("id", organization);
    final String accountData =
        toEncodedToken(
            asMap(
                claim,
                List.of(orgMap),
                "exp",
                expiresInSeconds,
                "name",
                "operate-testuser",
                DEFAULT_ORGANIZATIONS_KEY,
                List.of(Map.of("id", "3", "roles", List.of("user", "analyst")))));
    return new Tokens(
        "accessToken",
        emptyJSONEncoded + "." + accountData + "." + emptyJSONEncoded,
        "refreshToken",
        "type",
        5L);
  }

  private static String toEncodedToken(final Map map) {
    return toBase64(toJSON(map));
  }

  private static String toBase64(final String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  private static String toJSON(final Map map) {
    return Json.createObjectBuilder(map).build().toString();
  }

  @Before
  public void setUp() {
    new SpringContextHolder().setApplicationContext(applicationContext);
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

  @Test
  public void testHandleInvalidRequestException() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));
    doThrow(
            new Auth0ServiceException(
                new Exception("Invalid response code from the auth0-sandbox: HTTP 502.")))
        .when(auth0Service)
        .authenticate(any(), any());
    response = get(SSO_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            "logout",
            operateProperties.getAuth0().getClientId(),
            urlFor(ROOT));
  }

  @Test
  public void testLoginSuccess() throws Exception {
    mockPermissionAllowed();
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));

    response = get(ROOT, cookies);
    // Check if access to url possible
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThatSecurityHeadersAreSet(response);
  }

  @Test
  public void testLoginFailedWithNoReadPermissions() throws Exception {
    mockNoReadPermission();
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, cookies);
    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginSucceedWithNoWritePermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockNoWritePermission();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));

    get(SSO_CALLBACK_URI, cookies);

    final TokenAuthentication authentication =
        new TokenAuthentication(
            operateProperties.getAuth0(), operateProperties.getCloud().getOrganizationId());
    assertThat(authentication.getPermissions().contains(Permission.WRITE)).isEqualTo(false);

    // successfully redirect to root even without write permission
    response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockNoReadPermission();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri with invalid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(operateProperties.getAuth0().getClaimName(), "wrong-organization"));

    response = get(SSO_CALLBACK_URI, cookies);
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
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class)
        .when(authenticationController)
        .handle(any(), any());

    response = get(SSO_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(NO_PERMISSION));
  }

  @Test
  public void testLogout() throws Throwable {
    // Step 1 Login
    mockPermissionAllowed();
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);
    response = get(LOGIN_RESOURCE, cookies);
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));
    response = get(SSO_CALLBACK_URI, cookies);
    response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Step 2 logout
    response = get(LOGOUT_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            "logout",
            operateProperties.getAuth0().getClientId(),
            urlFor(ROOT));
    // Redirected to Login
    response = get(ROOT);
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginToAPIResource() throws Exception {
    // Step 1 try to access user info
    final String userInfoUrl = AuthenticationRestService.AUTHENTICATION_URL + "/user";
    ResponseEntity<String> response = get(userInfoUrl);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    HttpEntity<?> httpEntity = new HttpEntity<>(new HashMap<>());
    // Step 2 Get Login provider url
    mockPermissionAllowed();
    mockClusterMetadata();
    response = get(LOGIN_RESOURCE, httpEntity);

    assertThat(redirectLocationIn(response))
        .contains(
            operateProperties.getAuth0().getDomain(),
            SSO_CALLBACK_URI,
            operateProperties.getAuth0().getClientId(),
            operateProperties.getAuth0().getDomain());
    // Step 3 Call back uri
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                operateProperties.getAuth0().getClaimName(),
                operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, httpEntity);
    httpEntity = httpEntityWithCookie(response);

    // Test no ClusterMetadata
    mockEmptyClusterMetadata();
    response = get(userInfoUrl, httpEntity);
    assertThat(response.getBody()).contains("\"c8Links\":{}");

    mockClusterMetadata();
    response = get(userInfoUrl, httpEntity);
    final String c8Links =
        "{\"console\":\"https://console.audience/org/3/cluster/test-clusterId\","
            + "\"operate\":\"http://operate-url\","
            + "\"optimize\":\"http://optimize-url\","
            + "\"modeler\":\"https://modeler.audience/org/3\","
            + "\"tasklist\":\"http://tasklist-url\","
            + "\"zeebe\":\"grpc://zeebe-url\"}";
    assertThat(response.getBody()).contains("\"displayName\":\"operate-testuser\"");
    assertThat(response.getBody()).contains("\"username\":\"operate-testuser\"");
    assertThat(response.getBody()).contains("\"salesPlanType\":\"test\"");
    assertThat(response.getBody()).contains("\"roles\":[\"user\",\"analyst\"]");
    assertThat(response.getBody()).contains("\"c8Links\":" + c8Links);
  }

  @Test
  public void testCanParseResponseWithConnectorsUrl() throws Exception {

    // given
    final String jsonResponse =
        "[{\"uuid\":null,\"name\":null,\"urls\":{\"connectors\":\"http://connectors-url\"}}]";

    // when
    final ClusterMetadata[] clusterMetadatas =
        objectMapper.readValue(jsonResponse, ClusterMetadata[].class);

    // then
    assertThat(clusterMetadatas.length).isEqualTo(1);
    assertThat(clusterMetadatas[0].getUrls().get(ClusterMetadata.AppName.CONNECTORS))
        .isEqualTo("http://connectors-url");
  }

  private HttpEntity<?> httpEntityWithCookie(final ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", response.getHeaders().get("Set-Cookie").get(0));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Operate");
  }

  protected void assertThatRequestIsRedirectedTo(
      final ResponseEntity<?> response, final String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private ResponseEntity<String> get(final String path, final HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  private String urlFor(final String path) {
    return String.format("http://localhost:%d%s%s", randomServerPort, CONTEXT_PATH, path);
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private void mockPermissionAllowed() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
            eq("https://permissionurl/3"), eq(HttpMethod.GET), (HttpEntity) any(), (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private void mockNoReadPermission() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(false, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
            eq("https://permissionurl/3"), eq(HttpMethod.GET), (HttpEntity) any(), (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private void mockNoWritePermission() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, false, false, false));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
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
