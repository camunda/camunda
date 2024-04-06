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
package io.camunda.tasklist.webapp.security.se;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.metric.MetricIT;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.se.store.UserStore;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** This tests: authentication and security over GraphQL API /currentUser to get current user */
@ActiveProfiles({AUTH_PROFILE, "test"})
public class AuthenticationIT extends TasklistIntegrationTest implements AuthenticationTestable {

  private static final String GRAPHQL_URL = "/graphql";
  private static final String CURRENT_USER_QUERY =
      "{currentUser{ userId \n displayName salesPlanType roles}}";

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private PasswordEncoder encoder;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserStore userStore;

  @BeforeEach
  public void setUp() {
    final UserEntity user =
        new UserEntity()
            .setUserId(USERNAME)
            .setPassword(encoder.encode(PASSWORD))
            .setRoles(List.of(Role.OPERATOR.name()));
    given(userStore.getByUserId(USERNAME)).willReturn(user);
  }

  @Test
  public void testLoginSuccess() {
    // given
    // when
    final ResponseEntity<Void> response = login(USERNAME, PASSWORD);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(response);
    assertThatClientConfigContains("\"canLogout\":true");
  }

  @Test
  public void shouldFailWhileLogin() {
    // when
    final ResponseEntity<Void> response = login(USERNAME, String.format("%s%d", PASSWORD, 123));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldResetCookie() {
    // given
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(loginResponse);
    // when
    final ResponseEntity<String> logoutResponse = logout(loginResponse);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse);
  }

  @Test
  public void shouldReturnIndexPageForUnknownURI() {
    // given
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // when
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            "/does-not-exist",
            HttpMethod.GET,
            prepareRequestWithCookies(loginResponse.getHeaders()),
            String.class);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    // TODO: How can we check that this is the index page?
    // assertThat(responseEntity.getBody()).contains("<!doctype html><html lang=\"en\">");
  }

  @Test
  public void shouldReturnCurrentUser() {
    // given authenticated user
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);
    assertThatCookiesAreSet(loginResponse);
    // when
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    final GraphQLResponse response = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(response.get("$.data.currentUser.userId")).isEqualTo(USERNAME);
    assertThat(response.get("$.data.currentUser.displayName")).isEqualTo(USERNAME);
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    // when user is logged in
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // then endpoint are accessible
    ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    // when user logged out
    final ResponseEntity<String> logoutResponse = logout(loginResponse);

    // then endpoint is not accessible
    responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(logoutResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    assertThat(responseEntity.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void testCanAccessMetricsEndpoint() {
    final ResponseEntity<String> response =
        testRestTemplate.getForEntity("/actuator", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("actuator/info");

    final ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity(MetricIT.ENDPOINT, String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }

  @Test
  public void testCanReadAndWriteLoggersActuatorEndpoint() throws JSONException {
    ResponseEntity<String> response =
        testRestTemplate.getForEntity("/actuator/loggers/io.camunda.tasklist", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\":\"DEBUG\"");

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<String> request =
        new HttpEntity<>(new JSONObject().put("configuredLevel", "TRACE").toString(), headers);
    response =
        testRestTemplate.postForEntity(
            "/actuator/loggers/io.camunda.tasklist", request, String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(204);

    response = testRestTemplate.getForEntity("/actuator/loggers/io.camunda.tasklist", String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\":\"TRACE\"");
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private void assertThatClientConfigContains(final String text) {
    final ResponseEntity<String> clientConfigContent =
        testRestTemplate.getForEntity("/client-config.js", String.class);
    assertThat(clientConfigContent.getBody()).contains(text);
  }
}
