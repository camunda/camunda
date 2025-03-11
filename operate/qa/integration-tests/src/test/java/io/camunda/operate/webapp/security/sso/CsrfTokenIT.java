/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.OperateProfileService.SSO_AUTH_PROFILE;
import static io.camunda.config.operate.Auth0Properties.DEFAULT_ORGANIZATIONS_KEY;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.Tokens;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.auth.RolePermissionService;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo.SalesPlan;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import jakarta.json.Json;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
      ProcessRestService.class,
      ProcessDefinitionController.class,
      CamundaSecurityProperties.class,
    },
    properties = {
      "server.servlet.context-path=" + CsrfTokenIT.CONTEXT_PATH,
      "camunda.operate.auth0.clientId=1",
      "camunda.operate.auth0.clientSecret=2",
      "camunda.operate.cloud.organizationId=3",
      "camunda.operate.cloud.clusterId=test-clusterId",
      "camunda.operate.auth0.domain=domain",
      "camunda.operate.auth0.claimName=claimName",
      "camunda.operate.cloud.permissionaudience=audience",
      "camunda.operate.cloud.permissionurl=https://permissionurl",
      "camunda.operate.cloud.consoleUrl=https://consoleUrl"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(SSO_AUTH_PROFILE)
public class CsrfTokenIT {

  public static final SalesPlan OPERATE_TEST_SALESPLAN = new SalesPlan("test");
  public static final String CONTEXT_PATH = "/operate-test";
  @ClassRule public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();
  private final BiFunction<String, String, Tokens> orgExtractor =
      CsrfTokenIT::tokensWithOrgAsMapFrom;
  @LocalServerPort private int randomServerPort;
  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private OperateProperties operateProperties;
  @MockBean private AuthenticationController authenticationController;
  @Autowired private ApplicationContext applicationContext;

  @MockBean
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  @MockBean private ProcessReader processReader;
  @MockBean private ProcessInstanceReader processInstanceReader;
  @MockBean private BatchOperationWriter batchOperationWriter;
  @MockBean private ProcessDefinitionDao processDefinitionDao;
  @MockBean private PermissionsService permissionsService;

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
  public void testAccessToInternalAPIWithoutCSRF() throws Exception {
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

    final var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // Set session cookie - otherwise you get an 401 UNAUTHORIZED
    headers.add("Cookie", cookies.getHeaders().get("Cookie").getFirst());
    final var request = new HttpEntity<>("{}", headers);
    final var internalAPIresponse =
        testRestTemplate.postForEntity("/api/processes/grouped", request, Object.class);
    assertThat(internalAPIresponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  public void testAccessToInternalAPIWithCSRF() throws Exception {
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

    final var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // Set session cookie - otherwise you get an 401 UNAUTHORIZED
    headers.add("Cookie", cookies.getHeaders().get("Cookie").getFirst());
    // Add CSRF token as cookie - otherwise you get an 403 FORBIDDEN
    getCsrfCookie(response).ifPresent(csrfCookie -> headers.add("Cookie", csrfCookie));
    // Add CSRF token also as header - otherwise you get an 403 FORBIDDEN
    headers.set(X_CSRF_TOKEN, response.getHeaders().get(X_CSRF_TOKEN).getFirst());
    final var request = new HttpEntity<>("{}", headers);
    final var internalAPIresponse =
        testRestTemplate.postForEntity("/api/processes/grouped", request, Object.class);
    assertThat(internalAPIresponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void requestToPublicAPIShouldSucceedWithoutCSRF() throws Exception {
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

    final var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("Cookie", cookies.getHeaders().get("Cookie").getFirst());
    headers.setBearerAuth("bearerToken");
    final var request = new HttpEntity<>("{}", headers);
    final var publicAPIresponse =
        testRestTemplate.postForEntity("/v1/process-definitions/search", request, Object.class);
    assertThat(publicAPIresponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private HttpEntity<?> httpEntityWithCookie(final ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", response.getHeaders().get("Set-Cookie").getFirst());
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

  private void mockPermissionAllowed() {
    final ClusterInfo.OrgPermissions operate =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(operate, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, OPERATE_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
            eq("https://permissionurl/3"), eq(HttpMethod.GET), any(), (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private Optional<String> getCsrfCookie(final ResponseEntity<?> response) {
    return getCookies(response).stream().filter(key -> key.startsWith(X_CSRF_TOKEN)).findFirst();
  }

  private String redirectLocationIn(final ResponseEntity<?> response) {
    final URI location = response.getHeaders().getLocation();
    if (location != null) {
      return location.toString();
    }
    return null;
  }

  private List<String> getCookies(final ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get("Set-Cookie")).orElse(List.of());
  }

  private ResponseEntity<String> get(final String path) {
    return testRestTemplate.getForEntity(path, String.class);
  }
}
