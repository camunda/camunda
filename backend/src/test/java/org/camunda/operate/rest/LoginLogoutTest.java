package org.camunda.operate.rest;

import org.camunda.operate.security.WebSecurityConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Svetlana Dorokhova.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class, WebSecurityConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class LoginLogoutTest {

  public static final String USERNAME = "demo";
  public static final String PASSWORD = "demo";
  public static final String SESSION_ID_HEADER = "JSESSIONID";

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void testLogin() {
    ResponseEntity<Object> response = testRestTemplate.postForEntity("/login?user={user},password={password}", null, Object.class, USERNAME, PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);   //TODO check if this status is OK for Frontend
    assertThat(response.getHeaders()).containsKey("Set-Cookie");
    assertThat(response.getHeaders().get("Set-Cookie").get(0)).contains(SESSION_ID_HEADER);

  }


}
