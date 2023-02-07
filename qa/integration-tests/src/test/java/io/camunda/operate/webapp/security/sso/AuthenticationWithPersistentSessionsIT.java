/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.property.Auth0Properties.DEFAULT_ORGANIZATIONS_KEY;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateProfileService.SSO_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.sso.AuthenticationIT.OPERATE_TEST_SALESPLAN;
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
import io.camunda.operate.es.ElasticsearchTask;
import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.management.ElsIndicesCheck;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.AuthenticationRestService;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.ElasticsearchSessionRepository;
import io.camunda.operate.webapp.security.ElasticsearchSessionRepositoryConfigurator;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.es.RolePermissionService;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.camunda.operate.webapp.security.sso.model.ClusterMetadata;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.client.RestTemplate;

@RunWith(Parameterized.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        OAuth2WebConfigurer.class,
        Jwt2AuthenticationTokenConverter.class,
        CCSaaSJwtAuthenticationTokenValidator.class,
        SSOWebSecurityConfig.class,
        SSOConfigurator.class,
        Auth0Service.class,
        C8ConsoleService.class,
        SSOController.class,
        TokenAuthentication.class,
        SSOUserService.class,
        AuthenticationRestService.class,
        RolePermissionService.class,
        OperateURIs.class,
        OperateProperties.class,
        ElasticsearchSessionRepository.class,
        ElasticsearchSessionRepositoryConfigurator.class,
        OperateWebSessionIndex.class,
        RetryElasticsearchClient.class,
        ElasticsearchTask.class,
        OperateProfileService.class
    },
    properties = {
        "server.servlet.context-path=" + AuthenticationWithPersistentSessionsIT.CONTEXT_PATH,
        "camunda.operate.persistentSessionsEnabled=true",
        "camunda.operate.auth0.clientId=1",
        "camunda.operate.auth0.clientSecret=2",
        "camunda.operate.cloud.organizationid=3",
        "camunda.operate.cloud.clusterId=test-clusterId",
        "camunda.operate.auth0.domain=domain",
        "camunda.operate.cloud.permissionaudience=audience",
        "camunda.operate.cloud.permissionurl=https://permissionurl",
        "camunda.operate.cloud.consoleUrl=https://consoleUrl",
        "camunda.operate.auth0.claimName=claimName"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(SSO_AUTH_PROFILE)
public class AuthenticationWithPersistentSessionsIT implements AuthenticationTestable {

  public final static String CONTEXT_PATH = "/operate-test";
  @ClassRule
  public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule
  public final SpringMethodRule springMethodRule = new SpringMethodRule();

  @LocalServerPort
  private int randomServerPort;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @MockBean
  private AuthenticationController authenticationController;

  @MockBean
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  @MockBean
  private ElsIndicesCheck probes;

  private final BiFunction<String, String, Tokens> orgExtractor;

  public AuthenticationWithPersistentSessionsIT(BiFunction<String, String, Tokens> orgExtractor) {
    this.orgExtractor = orgExtractor;
  }

  @Parameters
  public static Collection<BiFunction<String, String, Tokens>> orgExtractors() {
    return Arrays.asList(AuthenticationWithPersistentSessionsIT::tokensWithOrgAsMapFrom);
  }

  @Before
  public void setUp() {
    // mock building authorizeUrl
    AuthorizeUrl mockedAuthorizedUrl = mock(AuthorizeUrl.class);
    given(authenticationController.buildAuthorizeUrl(isNotNull(), isNotNull(), isNotNull()))
        .willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withAudience(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withScope(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.build()).willReturn(
        "https://domain/authorize?redirect_uri=http://localhost:58117/sso-callback&client_id=1&audience=https://domain/userinfo");
  }

  @Test
  public void testLoginSuccess() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockPermissionAllowed();
    mockClusterMetadata();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getDomain()
    );
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(orgExtractor.apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));

    response = get(SSO_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));

    response = get(ROOT, cookies);
    // Check if access to url possible
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThatSecurityHeadersAreSet(response);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getDomain()
    );
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
    HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getDomain()
    );
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class).when(authenticationController)
        .handle(any(), any());

    response = get(SSO_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(NO_PERMISSION));
  }

  @Test
  public void testLogout() throws Throwable {
    // Step 1 Login
    mockPermissionAllowed();
    ResponseEntity<String> response = get(ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);
    response = get(LOGIN_RESOURCE, cookies);
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(orgExtractor.apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));
    response = get(SSO_CALLBACK_URI, cookies);
    response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Step 2 logout
    response = get(LOGOUT_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        "logout",
        operateProperties.getAuth0().getClientId(),
        urlFor(ROOT)
    );
    // Redirected to Login
    response = get(ROOT);
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginToAPIResource() throws Exception {
    // Step 1 try to access user info
    String userInfoUrl = AuthenticationRestService.AUTHENTICATION_URL + "/user";
    ResponseEntity<String> response = get(userInfoUrl);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);


    // Save cookie for further requests
    HttpEntity<?> httpEntity = httpEntityWithCookie(response);

    // Step 2 Get Login provider url
    mockPermissionAllowed();
    response = get(LOGIN_RESOURCE, httpEntity);

    assertThat(redirectLocationIn(response)).contains(
        operateProperties.getAuth0().getDomain(),
        SSO_CALLBACK_URI,
        operateProperties.getAuth0().getClientId(),
        operateProperties.getAuth0().getDomain()
    );
    // Step 3 Call back uri
    given(authenticationController.handle(isNotNull(), isNotNull())).willReturn(orgExtractor
        .apply(operateProperties.getAuth0().getClaimName(),
            operateProperties.getCloud().getOrganizationId()));


    response = get(SSO_CALLBACK_URI, httpEntity);
    httpEntity = httpEntityWithCookie(response);

    mockEmptyClusterMetadata();
    response = get(userInfoUrl, httpEntity);
    assertThat(response.getBody()).contains("\"c8Links\":{}" );

    mockClusterMetadata();
    response = get(userInfoUrl, httpEntity);
    final String c8Links = "{\"console\":\"https://console.audience/org/3/cluster/test-clusterId\","
        + "\"operate\":\"http://operate-url\","
        + "\"optimize\":\"http://optimize-url\","
        + "\"modeler\":\"https://modeler.audience/org/3\","
        + "\"tasklist\":\"http://tasklist-url\","
        + "\"zeebe\":\"grpc://zeebe-url\"}";
    assertThat(response.getBody()).contains("\"username\":\"operate-testuser\"");
    assertThat(response.getBody()).contains("\"displayName\":\"operate-testuser\"");
    assertThat(response.getBody()).contains("\"salesPlanType\":\"test\"");
    assertThat(response.getBody()).contains("\"roles\":[\"user\",\"analyst\"]");
    assertThat(response.getBody()).contains("\"c8Links\":"+c8Links);
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", response.getHeaders().get("Set-Cookie").get(0));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  @Test
  public void testAccessNoPermission() {
    ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Operate");
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  private String urlFor(String path) {
    return String.format("http://localhost:%d%s%s",randomServerPort, CONTEXT_PATH, path);
  }

  private static Tokens tokensWithOrgAsMapFrom(String claim, String organization) {
    String emptyJSONEncoded = toEncodedToken(Collections.EMPTY_MAP);
    long expiresInSeconds = System.currentTimeMillis() / 1000 + 10000; // now + 10 seconds
    Map<String, Object> orgMap = Map.of("id", organization);
    String accountData = toEncodedToken(asMap(
        claim, List.of(orgMap),
        "exp", expiresInSeconds,
        "name", "operate-testuser",
            DEFAULT_ORGANIZATIONS_KEY, List.of(Map.of("id","3", "roles",List.of("user","analyst")))
    ));
    return new Tokens("accessToken", emptyJSONEncoded + "." + accountData + "." + emptyJSONEncoded,
        "refreshToken", "type", 5L);
  }

  private static String toEncodedToken(Map map) {
    return toBase64(toJSON(map));
  }

  private static String toBase64(String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  private static String toJSON(Map map) {
    return new JSONObject(map).toString();
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

  private void mockClusterMetadata(){
    ClusterMetadata clusterMetadata = new ClusterMetadata()
        .setName("test-cluster")
        .setUuid("test-clusterId")
        .setUrls(Map.of(
          ClusterMetadata.AppName.OPERATE,"http://operate-url",
          ClusterMetadata.AppName.TASKLIST,"http://tasklist-url",
          ClusterMetadata.AppName.OPTIMIZE,"http://optimize-url",
          ClusterMetadata.AppName.ZEEBE,"grpc://zeebe-url")
        );
    ClusterMetadata[] clusterMetadatas = new ClusterMetadata[]{clusterMetadata};
    when(restTemplate.exchange(
        eq("https://consoleUrl/external/organizations/3/clusters"), eq(HttpMethod.GET), (HttpEntity) any(), eq(ClusterMetadata[].class)))
        .thenReturn(new ResponseEntity<>(clusterMetadatas, HttpStatus.OK));
  }

  private void mockEmptyClusterMetadata(){
    when(restTemplate.exchange(
        eq("https://consoleUrl/external/organizations/3/clusters"), eq(HttpMethod.GET), (HttpEntity) any(), eq(ClusterMetadata[].class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
  }
}
