package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.AuthenticationRestService.AUTHENTICATION_URL;
import static org.camunda.operate.security.WebSecurityConfig.*;
import static org.camunda.operate.security.WebSecurityConfig.LOGOUT_RESOURCE;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.Map;

import org.camunda.operate.TestApplication;
import org.camunda.operate.rest.dto.UserDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * @author Svetlana Dorokhova.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("auth")
public class AuthenticationTest {

  public static final String CURRENT_USER_URL = AUTHENTICATION_URL + "/user";

  public static final String USERNAME = "demo";
  public static final String PASSWORD = "demo";

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void shouldSetCookie() {
    // given
    HttpEntity<MultiValueMap<String, String>> request = prepareLoginRequest(USERNAME, PASSWORD);

    // when
    ResponseEntity<Void> response = login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders()).containsKey("Set-Cookie");
    assertThat(response.getHeaders().get("Set-Cookie").get(0)).contains(COOKIE_JSESSIONID);
  }

  @Test
  public void shouldFailWhileLogin() {
    // given
    HttpEntity<MultiValueMap<String, String>> request = prepareLoginRequest(USERNAME, String.format("%s%d", PASSWORD, 123));

    // when
    ResponseEntity<Void> response = login(request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldResetCookie() {
    // given
    HttpEntity<MultiValueMap<String, String>> loginRequest = prepareLoginRequest(USERNAME, PASSWORD);
    ResponseEntity<Void> loginResponse = login(loginRequest);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(loginResponse.getHeaders()).containsKey("Set-Cookie");
    assertThat(loginResponse.getHeaders().get("Set-Cookie").get(0)).contains(COOKIE_JSESSIONID);

    String session = loginResponse.getHeaders().get("Set-Cookie").get(0);

    HttpEntity<Map<String, String>> logoutRequest = prepareRequestWithCookies(session);

    // when
    ResponseEntity<String> logoutResponse = logout(logoutRequest);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(logoutResponse.getHeaders()).containsKey("Set-Cookie");
    assertThat(logoutResponse.getHeaders().get("Set-Cookie").get(0)).contains(COOKIE_JSESSIONID + "=;");
  }

  @Test
  public void shouldReturnCurrentUser() {
    //given authenticated user
    HttpEntity<MultiValueMap<String, String>> loginRequest = prepareLoginRequest(USERNAME, PASSWORD);
    ResponseEntity<Void> loginResponse = login(loginRequest);
    String session = loginResponse.getHeaders().get("Set-Cookie").get(0);

    //when
    final ResponseEntity<UserDto> responseEntity = testRestTemplate.exchange(CURRENT_USER_URL, HttpMethod.GET,
      prepareRequestWithCookies(session), UserDto.class);

    //then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody().getFirstname()).isNotEmpty();
    assertThat(responseEntity.getBody().getLastname()).isNotEmpty();
  }

  protected HttpEntity<MultiValueMap<String, String>> prepareLoginRequest(String username, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", password);

    return new HttpEntity<>(body, headers);
  }

  protected HttpEntity<Map<String, String>> prepareRequestWithCookies(String session) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", session);

    Map<String, String> body = new HashMap<>();

    return new HttpEntity<>(body, headers);
  }

  protected ResponseEntity<Void> login(HttpEntity<MultiValueMap<String, String>> request) {
    return testRestTemplate.postForEntity(LOGIN_RESOURCE, request, Void.class);
  }

  protected ResponseEntity<String> logout(HttpEntity<Map<String, String>> request) {
    return testRestTemplate.postForEntity(LOGOUT_RESOURCE, request, String.class);
  }

}
