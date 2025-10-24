/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.property.TasklistProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      "camunda.webapps.enabled = true",
      "camunda.webapps.default-app = tasklist",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "camunda.security.authentication.unprotected-api = true",
      "camunda.security.authorizations.enabled = false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"tasklist", "standalone", "test"})
public abstract class TasklistIntegrationTest {

  protected OffsetDateTime testStartTime;
  @Autowired private ApplicationContext applicationContext;

  @BeforeEach
  public void before() {
    testStartTime = OffsetDateTime.now();
    new SpringContextHolder().setApplicationContext(applicationContext);
  }
}
