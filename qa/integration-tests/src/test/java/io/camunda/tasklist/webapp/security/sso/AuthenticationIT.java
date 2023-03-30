/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.property.Auth0Properties.DEFAULT_ORGANIZATIONS_KEY;
import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.sso.AuthSSOApplication;
import io.camunda.tasklist.webapp.graphql.entity.C8AppLink;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.sso.model.ClusterInfo;
import io.camunda.tasklist.webapp.security.sso.model.ClusterInfo.SalesPlan;
import io.camunda.tasklist.webapp.security.sso.model.ClusterMetadata;
import java.util.*;
import java.util.function.BiFunction;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.client.RestTemplate;

@RunWith(Parameterized.class)
@SpringBootTest(
    classes = {AuthSSOApplication.class},
    properties = {
      "camunda.tasklist.auth0.clientId=1",
      "camunda.tasklist.auth0.clientSecret=2",
      "camunda.tasklist.auth0.organization=3",
      "camunda.tasklist.cloud.clusterId=test-clusterId",
      "camunda.tasklist.auth0.domain=domain",
      "camunda.tasklist.auth0.claimName=claimName",
      "camunda.tasklist.cloud.permissionaudience=audience",
      "camunda.tasklist.cloud.permissionurl=https://permissionurl",
      "camunda.tasklist.cloud.consoleUrl=https://consoleUrl",
      "server.servlet.session.cookie.name = " + TasklistURIs.COOKIE_JSESSIONID
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({SSO_AUTH_PROFILE, "test"})
public class AuthenticationIT implements AuthenticationTestable {

  @ClassRule public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

  public static final SalesPlan TASKLIST_TEST_SALESPLAN = new SalesPlan("test");
  public static final List<String> TASKLIST_TEST_ROLES = List.of("user", "analyst");
  private static final String COOKIE_KEY = "Cookie";
  private static final String TASKLIST_TESTUSER = "tasklist-testuser";

  private static final String TASKLIST_TESTUSER_EMAIL = "testuser@tasklist.io";
  @Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();
  private final BiFunction<String, String, Tokens> orgExtractor;

  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private BeanFactory beanFactory;
  @MockBean private AuthenticationController authenticationController;

  @MockBean private AssigneeMigrator assigneeMigrator;

  @MockBean
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @LocalServerPort private int randomServerPort;

  @SpyBean private Auth0Service auth0Service;

  public AuthenticationIT(BiFunction<String, String, Tokens> orgExtractor) {
    this.orgExtractor = orgExtractor;
  }

  @Parameters
  public static Collection<BiFunction<String, String, Tokens>> orgExtractors() {
    return List.of(AuthenticationIT::tokensWithOrgAsMapFrom);
  }

  private static Tokens tokensWithOrgAsMapFrom(String claim, String organization) {
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

  private static String toEncodedToken(Map<String, ?> map) {
    return toBase64(toJSON(map));
  }

  private static String toBase64(String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }

  private static String toJSON(Map<String, ?> map) {
    return new JSONObject(map).toString();
  }

  @Before
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

  @Test
  public void testLoginSuccess() throws Exception {
    final HttpEntity<?> cookies = loginWithSSO();
    final ResponseEntity<String> response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThatClientConfigContains("\"canLogout\":false");
  }

  private void assertThatClientConfigContains(final String text) {
    final ResponseEntity<String> clientConfigContent =
        testRestTemplate.getForEntity("/client-config.js", String.class);
    assertThat(clientConfigContent.getBody()).contains(text);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    mockPermissionAllowed();
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
            tasklistProperties.getAuth0().getDomain(),
            SSO_CALLBACK,
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getDomain());
    // Step 3 Call back uri with valid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                tasklistProperties.getAuth0().getClaimName(),
                tasklistProperties.getAuth0().getOrganization()));

    response = get(SSO_CALLBACK, cookies);
    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginSucceedWithNoWritePermissions() throws Exception {
    mockNoWritePermission();
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
    // Step 3 Call back uri with valid userdata
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                tasklistProperties.getAuth0().getClaimName(),
                tasklistProperties.getAuth0().getOrganization()));

    get(SSO_CALLBACK, cookies);

    final TokenAuthentication authentication = beanFactory.getBean(TokenAuthentication.class);
    assertThat(authentication.getPermissions().contains(Permission.WRITE)).isEqualTo(false);

    // successfully redirect to root even without write permission
    response = get(ROOT, cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThatClientConfigContains("\"canLogout\":false");
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
  @DirtiesContext
  public void testLoginAndUsageOfGraphQlApi() throws Exception {
    // Step 1: try to access current user
    final ResponseEntity<String> currentUserResponse =
        getCurrentUserByGraphQL(new HttpEntity<>(new HttpHeaders()));
    final HttpEntity<?> cookies = httpEntityWithCookie(currentUserResponse);

    assertThatUnauthorizedIsReturned(currentUserResponse);

    // Step 2: Get Login provider url
    mockPermissionAllowed();

    final ResponseEntity<String> loginResponse = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(loginResponse))
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

    final HttpEntity<?> loggedCookies = loginWithSSO();
    ResponseEntity<String> responseEntity = getCurrentUserByGraphQL(loggedCookies);
    GraphQLResponse graphQLResponse = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(graphQLResponse.get("$.data.currentUser.c8Links", List.class)).isEmpty();
    // Test with ClusterMetadata
    mockClusterMetadata();
    // when
    responseEntity = getCurrentUserByGraphQL(loggedCookies);

    // then
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
  @DirtiesContext
  public void testLoginAndUsageOfRestApi() throws Exception {
    // Step 1: try to access current user
    final ResponseEntity<String> currentUserResponse =
        getCurrentUserByRestApi(new HttpEntity<>(new HttpHeaders()));
    final HttpEntity<?> cookies = httpEntityWithCookie(currentUserResponse);
    assertThatUnauthorizedIsReturned(currentUserResponse);

    // Step 2: Get Login provider url
    mockPermissionAllowed();

    final ResponseEntity<String> loginResponse = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(loginResponse))
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

    // Test with ClusterMetadata
    mockClusterMetadata();

    // when
    final ResponseEntity<String> currentUserResponseAfterLogin =
        getCurrentUserByRestApi(loginWithSSO());
    final UserDTO currentUserAfterLogin =
        objectMapper.readValue(currentUserResponseAfterLogin.getBody(), UserDTO.class);

    // then
    assertThat(currentUserResponseAfterLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(currentUserAfterLogin.getUserId()).isEqualTo(TASKLIST_TESTUSER_EMAIL);
    assertThat(currentUserAfterLogin.getDisplayName()).isEqualTo(TASKLIST_TESTUSER);
    assertThat(currentUserAfterLogin.getSalesPlanType())
        .isEqualTo(TASKLIST_TEST_SALESPLAN.getType());
    assertThat(currentUserAfterLogin.getRoles()).isEqualTo(TASKLIST_TEST_ROLES);
    assertThat(currentUserAfterLogin.getC8Links())
        .extracting("name", "link")
        .containsExactly(
            tuple("console", "https://console.audience/org/3/cluster/test-clusterId"),
            tuple("operate", "http://operate-url"),
            tuple("optimize", "http://optimize-url"),
            tuple("modeler", "https://modeler.audience/org/3"),
            tuple("tasklist", "http://tasklist-url"),
            tuple("zeebe", "grpc://zeebe-url"));
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

  private void assertThatUnauthorizedIsReturned(ResponseEntity<?> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }

  private String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private HttpEntity<?> loginWithSSO() throws IdentityVerificationException {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
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
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    mockPermissionAllowed();
    given(authenticationController.handle(isNotNull(), isNotNull()))
        .willReturn(
            orgExtractor.apply(
                tasklistProperties.getAuth0().getClaimName(),
                tasklistProperties.getAuth0().getOrganization()));

    get(SSO_CALLBACK, cookies);
    return cookies;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    if (response.getHeaders().containsKey(HttpHeaders.SET_COOKIE)) {
      headers.add(
          COOKIE_KEY,
          response.getHeaders().get(HttpHeaders.SET_COOKIE).stream().findFirst().orElse(""));
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

  private void mockNoReadPermission() {
    final ClusterInfo.OrgPermissions tasklist =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(false, true, true, true));

    final ClusterInfo.OrgPermissions cluster = new ClusterInfo.OrgPermissions(tasklist, null);
    final ClusterInfo clusterInfo = new ClusterInfo("Org Name", cluster, TASKLIST_TEST_SALESPLAN);
    final ResponseEntity<ClusterInfo> clusterInfoResponseEntity =
        new ResponseEntity<>(clusterInfo, HttpStatus.OK);

    when(restTemplate.exchange(
            eq("https://permissionurl/3"), eq(HttpMethod.GET), (HttpEntity) any(), (Class) any()))
        .thenReturn(clusterInfoResponseEntity);
  }

  private void mockNoWritePermission() {
    final ClusterInfo.OrgPermissions tasklist =
        new ClusterInfo.OrgPermissions(null, new ClusterInfo.Permission(true, false, false, false));

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
