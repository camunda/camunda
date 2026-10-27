/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.rest.config.JacksonConfig;
import io.camunda.zeebe.gateway.rest.config.OpenApiConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration"
    })
@Import({JacksonConfig.class})
@AutoConfigureWebTestClient
public abstract class RestTest {

  @Autowired protected WebTestClient webClient;
  @MockitoBean protected OpenApiConfigurer openApiConfigurer;
}
