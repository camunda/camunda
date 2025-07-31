/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Disabled("Will be addressed in other issues")
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.thymeleaf.prefix = classpath:/META-INF/resources/",
      "server.servlet.context-path = " + IndexControllerIT.CONTEXT_PATH,
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
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
