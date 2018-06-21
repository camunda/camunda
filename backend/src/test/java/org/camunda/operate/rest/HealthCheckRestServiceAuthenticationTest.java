package org.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Tests the health check with enabled auhtentication.
 * @author Svetlana Dorokhova.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class},
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("auth")
public class HealthCheckRestServiceAuthenticationTest {

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void testHealthStateEndpointIsSecured() {
    final ResponseEntity<String> response = testRestTemplate.getForEntity(HealthCheckRestService.HEALTH_CHECK_URL, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

}
