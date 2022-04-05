/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.util.TestApplication;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@Ignore("Will be addressed in other issues")
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.thymeleaf.prefix = classpath:/META-INF/resources/",
      "server.servlet.context-path = " + IndexControllerIT.CONTEXT_PATH
    })
public class IndexControllerIT {

  static final String CONTEXT_PATH = "/context-path-test/";

  @Autowired private TestRestTemplate webclient;

  @Test
  public void shouldReturnCurrentContextPath() {
    final String baseTagWithContextPath = String.format("<base href=\"%s\"/>", CONTEXT_PATH);
    final ResponseEntity<String> response = webclient.getForEntity("/login", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains(baseTagWithContextPath);
  }
}
