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
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.USERS_URL_V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public interface AuthenticationTestable {

  String SET_COOKIE_HEADER = "Set-Cookie";

  String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

  String CURRENT_USER_QUERY =
      "{currentUser{ userId \n displayName roles salesPlanType c8Links { name link } }}";

  TestRestTemplate getTestRestTemplate();

  default HttpEntity<Map<String, ?>> prepareRequestWithCookies(HttpHeaders httpHeaders) {
    return prepareRequestWithCookies(httpHeaders, null);
  }

  default HttpEntity<Map<String, ?>> prepareRequestWithCookies(
      HttpHeaders httpHeaders, String graphQlQuery) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getCookiesAsString(httpHeaders));

    final HashMap<String, String> body = new HashMap<>();
    if (graphQlQuery != null) {
      body.put("query", graphQlQuery);
    }

    return new HttpEntity<>(body, headers);
  }

  default ResponseEntity<Void> login(String username, String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return getTestRestTemplate()
        .postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  default ResponseEntity<String> logout(ResponseEntity<?> response) {
    final HttpEntity<Map<String, ?>> request = prepareRequestWithCookies(response.getHeaders());
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default ResponseEntity<String> getCurrentUserByGraphQL(final HttpEntity<?> cookies) {
    return getTestRestTemplate()
        .exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(cookies.getHeaders(), CURRENT_USER_QUERY),
            String.class);
  }

  default ResponseEntity<String> getCurrentUserByRestApi(final HttpEntity<?> cookies) {
    return getTestRestTemplate()
        .exchange(
            USERS_URL_V1.concat("/current"),
            HttpMethod.GET,
            prepareRequestWithCookies(cookies.getHeaders(), null),
            String.class);
  }

  default void assertThatCookiesAreSet(ResponseEntity<?> response) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final String sessionCookie = getSessionCookie(headers).orElse("");
    assertThat(sessionCookie).contains(COOKIE_JSESSIONID);
    assertThat(sessionCookie).contains("SameSite=Lax");
    assertThat(headers).containsKey(CONTENT_SECURITY_POLICY_HEADER);
    assertThat(headers.get(CONTENT_SECURITY_POLICY_HEADER)).isNotNull().isNotEmpty();
  }

  default void assertThatCookiesAreDeleted(ResponseEntity<?> response) {
    final HttpHeaders headers = response.getHeaders();
    assertThat(headers).containsKey(SET_COOKIE_HEADER);
    final List<String> cookies = headers.get(SET_COOKIE_HEADER);
    final String emptyValue = "=;";
    assertThat(cookies).anyMatch((cookie) -> cookie.contains(COOKIE_JSESSIONID + emptyValue));
  }

  default ResponseEntity<String> get(String path) {
    return getTestRestTemplate().getForEntity(path, String.class);
  }

  default String getCookiesAsString(HttpHeaders headers) {
    return getSessionCookie(headers).orElse("");
  }

  default Optional<String> getSessionCookie(HttpHeaders headers) {
    return getCookies(headers).stream().filter(key -> key.contains(COOKIE_JSESSIONID)).findFirst();
  }

  default List<String> getCookies(HttpHeaders headers) {
    return Optional.ofNullable(headers.get(SET_COOKIE_HEADER)).orElse(List.of());
  }

  default String redirectLocationIn(ResponseEntity<?> response) {
    final URI uri = response.getHeaders().getLocation();
    return uri == null ? "" : uri.toString();
  }
}
