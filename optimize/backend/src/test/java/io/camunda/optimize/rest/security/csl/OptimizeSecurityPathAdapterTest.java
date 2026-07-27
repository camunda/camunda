/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OptimizeSecurityPathAdapterTest {

  // ACTUATOR_ENDPOINT is a mutable static bound from management.endpoints.web.base-path; capture
  // and restore it so a customized value in one test does not leak into others.
  private final String originalActuatorEndpoint = ACTUATOR_ENDPOINT;
  private final OptimizeSecurityPathAdapter pathAdapter = new OptimizeSecurityPathAdapter();

  @AfterEach
  void restoreActuatorEndpoint() {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = originalActuatorEndpoint;
  }

  @Test
  void shouldUnprotectActuatorAtDefaultBasePath() {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = "/actuator";
    assertThat(pathAdapter.unprotectedPaths()).contains("/actuator/**");
  }

  @Test
  void shouldUnprotectActuatorAtCustomBasePath() {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = "/management";
    assertThat(pathAdapter.unprotectedPaths()).contains("/management/**");
  }
}
