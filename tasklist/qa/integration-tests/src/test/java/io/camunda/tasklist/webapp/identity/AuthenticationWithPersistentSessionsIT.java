/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.identity;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IDENTITY_CALLBACK_URI;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.util.apps.identity.AuthIdentityApplication;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthentication;
import io.camunda.tasklist.webapp.security.identity.IdentityService;
import java.util.HashMap;
import org.assertj.core.util.DateUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {AuthIdentityApplication.class},
    properties = {
      "camunda.tasklist.identity.issuerBackendUrl=http://localhost:18080/auth/realms/camunda-platform",
      "camunda.tasklist.identity.issuerUrl=http://localhost:18080/auth/realms/camunda-platform",
      "camunda.tasklist.identity.clientId=tasklist",
      "camunda.tasklist.identity.clientSecret=the-cake-is-alive",
      "camunda.tasklist.identity.audience=tasklist-api",
      "server.servlet.session.cookie.name = " + COOKIE_JSESSIONID,
      "camunda.tasklist.persistentSessionsEnabled = true",
      "camunda.tasklist.importer.startLoadingDataOnStartup = false",
      "camunda.tasklist.archiver.rolloverEnabled = false",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({TasklistProfileService.IDENTITY_AUTH_PROFILE, "test"})
public class AuthenticationWithPersistentSessionsIT implements AuthenticationTestable {

  @LocalServerPort private int randomServerPort;

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private IdentityService identityService;

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Tasklist");
  }

  @Test
  public void testLoginSuccess() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
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
  public void testLoginFailedWithNoPermissions() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
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
  public void testLoginFailedWithNoReadPermissions() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    assertThatCookiesAreSet(response);
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
    headers.add("Cookie", getCookiesAsString(response.getHeaders()));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }
}
