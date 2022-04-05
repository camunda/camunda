/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.operate.util.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Ignore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "server.servlet.session.cookie.name = " + OperateURIs.COOKIE_JSESSIONID
    }
)
@ActiveProfiles({"test", "auth", "dev"})
public class ShowErrorMessagesInDevelopmentIT implements AuthenticationTestable{

  public static final String INVALID_JSON_PAYLOAD = "{ \"query\" :\n"
      + "    {\n"
      + "      \"incidents\" :true,\n"
      + "      \"running\" :xxx\n"      // <--- Invalid JSON
      + "    } ,\n"
      + "    \"sorting\":\n"
      + "     {\n"
      + "     \"sortBy\":\"processName\",\n"
      + "     \"sortOrder\":\"desc\"\n"
      + "     },\n"
      + "     \"pageSize\":5\n"
      + "}";
  @Autowired
  TestRestTemplate testRestTemplate;

  @Ignore("Failing because E2E tests were migrated to Github Actions")
  @Test
  public void shouldSuppressErrorMessage() {
    //given authenticated user
    ResponseEntity<Void> loginResponse = login("demo", "demo");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.add("Cookie", getSessionCookies(loginResponse).stream().findFirst().orElse(""));
    // when requesting with invalid JSON payload
    HttpEntity<String> request = new HttpEntity<>(INVALID_JSON_PAYLOAD, headers);
    final ResponseEntity<String> response = getTestRestTemplate()
        .postForEntity(PROCESS_INSTANCE_URL, request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String content = response.getBody();
    assertThat(content).contains("JSON parse error");
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

}
