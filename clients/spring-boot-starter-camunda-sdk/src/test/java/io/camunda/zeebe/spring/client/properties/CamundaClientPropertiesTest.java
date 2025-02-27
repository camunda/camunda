/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientPropertiesTest {

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {"camunda.client.auth.credentials-cache-path=/some/path"})
  class CredentialsCachePathConfigTest {
    @Autowired CamundaClientProperties camundaClientProperties;

    @Test
    void shouldApplyProperty() {
      assertThat(camundaClientProperties.getAuth().getCredentialsCachePath())
          .isEqualTo("/some/path");
    }
  }
}
