/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.webapp.security.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.IDENTITY_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.es.ElasticsearchTask;
import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.AuthenticationTestable;
import io.camunda.operate.webapp.security.ElasticsearchSessionRepository;
import io.camunda.operate.webapp.security.ElasticsearchSessionRepositoryConfigurator;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.SameSiteCookieTomcatContextCustomizer;
import io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator;
import io.camunda.operate.webapp.security.oauth2.IdentityJwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.IdentityOAuth2WebConfigurer;
import io.camunda.operate.webapp.security.oauth2.Jwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.security.oauth2.OAuth2WebConfigurer;
import java.util.HashMap;
import org.assertj.core.util.DateUtil;
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
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        OAuth2WebConfigurer.class,
        Jwt2AuthenticationTokenConverter.class,
        CCSaaSJwtAuthenticationTokenValidator.class,
        IdentityWebSecurityConfig.class,
        IdentityService.class,
        IdentityConfigurer.class,
        IdentityController.class,
        IdentityUserService.class,
        IdentityOAuth2WebConfigurer.class,
        IdentityJwt2AuthenticationTokenConverter.class,
        OperateURIs.class,
        OperateProperties.class,
        OperateProfileService.class,
        ElasticsearchSessionRepository.class,
        ElasticsearchSessionRepositoryConfigurator.class,
        OperateWebSessionIndex.class,
        RetryElasticsearchClient.class,
        ElasticsearchTask.class,
        SameSiteCookieTomcatContextCustomizer.class
    },
    properties = {
        "server.servlet.context-path=" + AuthenticationWithPersistentSessionsIT.CONTEXT_PATH,
        "camunda.operate.identity.issuerBackendUrl=http://localhost:18080/auth/realms/camunda-platform",
        "camunda.operate.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
        "camunda.operate.identity.clientId=operate",
        "camunda.operate.identity.clientSecret=the-cake-is-alive",
        "camunda.operate.identity.audience=operate-api",
        "server.servlet.session.cookie.name=" + COOKIE_JSESSIONID,
        "camunda.operate.persistentSessionsEnabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class AuthenticationWithPersistentSessionsIT implements AuthenticationTestable {

  public final static String CONTEXT_PATH = "/operate-test";

  @LocalServerPort
  private int randomServerPort;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @MockBean
  private IdentityService identityService;

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains(
        "No permission for Operate - Please check your operate configuration or cloud configuration.");
    assertThatSecurityHeadersAreSet(response);
  }

  @Test
  public void testLoginSuccess() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAndSecurityHeadersAreSet(response);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
    when(identityService.getRedirectUrl(any())).thenReturn("/redirected-to-identity");

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains("/redirected-to-identity");

    // Step 3 assume authentication will be successful
    final IdentityAuthentication identityAuthentication =
        new IdentityAuthentication().setExpires(DateUtil.tomorrow());
    identityAuthentication.setAuthenticated(true);

    when(identityService.getAuthenticationFor(any(), any())).thenReturn(identityAuthentication);

    // Step 4 do callback
    response = get(IDENTITY_CALLBACK_URI, cookies);

    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));
    // Step 5  check if access to url possible
    response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAndSecurityHeadersAreSet(response);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
    when(identityService.getRedirectUrl(any())).thenReturn("/redirected-to-identity");

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains("/redirected-to-identity");

    // Step 3 assume authentication will be fail
    when(identityService.getAuthenticationFor(any(), any()))
        .thenThrow(new RuntimeException("Something is going wrong"));

    response = get(IDENTITY_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithNoReadPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAndSecurityHeadersAreSet(response);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
    when(identityService.getRedirectUrl(any())).thenReturn("/redirected-to-identity");

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response)).contains("/redirected-to-identity");
    // Step 3 assume authentication succeed but return no READ permission
    when(identityService.getAuthenticationFor(any(), any()))
        .thenThrow(new InsufficientAuthenticationException("No read permissions"));

    response = get(IDENTITY_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", getCookies(response).get(0));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private String urlFor(String path) {
    return String.format("http://localhost:%d%s%s", randomServerPort, CONTEXT_PATH, path);
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }
}
