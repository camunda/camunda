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
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.USER_ENDPOINT;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.operate.property.WebSecurityProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.server.header.ContentSecurityPolicyServerHttpHeadersWriter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public interface AuthenticationTestable {

  String SET_COOKIE_HEADER = "Set-Cookie";

  // Frontend is relying on this - see e2e tests
  Pattern COOKIE_PATTERN =
      Pattern.compile(
          "^" + OperateURIs.COOKIE_JSESSIONID + "=[0-9A-Z]{32}$", Pattern.CASE_INSENSITIVE);
  String CURRENT_USER_URL = AUTHENTICATION_URL + USER_ENDPOINT;

  default HttpEntity<Map<String, String>> prepareRequestWithCookies(final ResponseEntity<?> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getSessionCookies(response).stream().findFirst().orElse(""));

    final Map<String, String> body = new HashMap<>();
    return new HttpEntity<>(body, headers);
  }

  default List<String> getCookies(final ResponseEntity<?> response) {
    return Optional.ofNullable(response.getHeaders().get(SET_COOKIE_HEADER)).orElse(List.of());
  }

  default List<String> getSessionCookies(final ResponseEntity<?> response) {
    return getCookies(response).stream()
        .filter(key -> key.contains(COOKIE_JSESSIONID))
        .collect(Collectors.toList());
  }

  default void assertThatCookiesAndSecurityHeadersAreSet(final ResponseEntity<?> response) {
    final List<String> cookies = getSessionCookies(response);
    assertThat(cookies).isNotEmpty();
    final String lastSetCookie = cookies.get(cookies.size() - 1);
    assertThat(lastSetCookie.split(";")[0]).matches(COOKIE_PATTERN);
    assertSameSiteIsSet(lastSetCookie);
    assertThatSecurityHeadersAreSet(response);
  }

  default void assertThatSecurityHeadersAreSet(final ResponseEntity<?> response) {
    final var cspHeaderValues =
        response
            .getHeaders()
            .getOrEmpty(ContentSecurityPolicyServerHttpHeadersWriter.CONTENT_SECURITY_POLICY);
    assertThat(cspHeaderValues).isNotEmpty();
    assertThat(cspHeaderValues)
        .first()
        .isIn(
            WebSecurityProperties.DEFAULT_SM_SECURITY_POLICY,
            WebSecurityProperties.DEFAULT_SAAS_SECURITY_POLICY);
  }

  default void assertSameSiteIsSet(final String cookie) {
    assertThat(cookie).contains("SameSite=Lax");
  }

  default void assertThatCookiesAreDeleted(final ResponseEntity<?> response) {
    final String emptyValue = "=;";
    final List<String> sessionCookies = getSessionCookies(response);
    if (!sessionCookies.isEmpty()) {
      final String lastSetCookie = sessionCookies.get(sessionCookies.size() - 1);
      assertThat(lastSetCookie).contains(COOKIE_JSESSIONID + emptyValue);
    }
  }

  default ResponseEntity<Void> login(final String username, final String password) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return getTestRestTemplate()
        .postForEntity(LOGIN_RESOURCE, new HttpEntity<>(body, headers), Void.class);
  }

  default ResponseEntity<?> logout(final ResponseEntity<?> previousResponse) {
    final HttpEntity<Map<String, String>> request = prepareRequestWithCookies(previousResponse);
    return getTestRestTemplate().postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

  default UserDto getCurrentUser(final ResponseEntity<?> previousResponse) {
    final ResponseEntity<UserDto> responseEntity =
        getTestRestTemplate()
            .exchange(
                CURRENT_USER_URL,
                HttpMethod.GET,
                prepareRequestWithCookies(previousResponse),
                UserDto.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    return responseEntity.getBody();
  }

  default ResponseEntity<String> get(final String path) {
    return getTestRestTemplate().getForEntity(path, String.class);
  }

  default String redirectLocationIn(final ResponseEntity<?> response) {
    final URI location = response.getHeaders().getLocation();
    if (location != null) {
      return location.toString();
    }
    return null;
  }

  TestRestTemplate getTestRestTemplate();
}
