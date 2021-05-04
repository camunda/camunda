/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.Application.SPRING_THYMELEAF_PREFIX_KEY;
import static io.camunda.operate.Application.SPRING_THYMELEAF_PREFIX_VALUE;

import io.camunda.operate.Application;
import io.camunda.operate.util.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        SPRING_THYMELEAF_PREFIX_KEY + " = " + SPRING_THYMELEAF_PREFIX_VALUE,
        "server.servlet.context-path = " + IndexControllerIT.CONTEXT_PATH
    }
)
public class IndexControllerIT {

  final static String CONTEXT_PATH = "/context-path-test/";

  @Autowired
  private TestRestTemplate webclient;

  @Test
  public void shouldReturnCurrentContextPath() {
    String baseTagWithContextPath = String.format("<base href=\"%s\"/>", CONTEXT_PATH);
    ResponseEntity<String> response = webclient.getForEntity("/login",String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains(baseTagWithContextPath);
  }
}
