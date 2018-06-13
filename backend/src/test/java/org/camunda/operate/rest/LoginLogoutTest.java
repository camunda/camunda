package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class LoginLogoutTest {

  public static final String USERNAME = "demo";
  public static final String PASSWORD = "demo";
  public static final String SESSION_ID_HEADER = "JSESSIONID";

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  public void testLogin() {

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
    map.add("username", USERNAME);
    map.add("password", PASSWORD);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

    ResponseEntity<Object> response = testRestTemplate.postForEntity("/api/login", request, Object.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders()).containsKey("Set-Cookie");
    assertThat(response.getHeaders().get("Set-Cookie").get(0)).contains(SESSION_ID_HEADER);

  }

  @Test
  public void testLoginFailed() {

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
    map.add("username", USERNAME);
    map.add("password", "wrongPsw");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

    ResponseEntity<String> response = testRestTemplate.postForEntity("/api/login", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("Bad credentials");

  }

  @Test
  public void testLogout() {
    //TODO

  }

}
