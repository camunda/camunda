/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

/**
 * Locks in the default behavior when neither opt-out property is set: both Operate's and Tasklist's
 * V1 {@code GET /v1/variables/{id}} controllers remain registered in the same context, and Spring's
 * ambiguous-mapping check rejects every request with a 500.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class VariablesV1DefaultAmbiguousMappingIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA = new TestCamundaApplication();

  @Test
  void shouldReturn500WithAmbiguousMappingByDefault() throws Exception {
    // when
    final HttpResponse<String> response;
    try (final var client = STANDALONE_CAMUNDA.newOperateClient()) {
      response = client.sendGetRequest("v1/variables/%s", 1L);
    }

    // then
    // Only asserting status: the ambiguous-mapping exception fires before either V1 controller
    // runs, so the body is generic Spring error output, not the V1 Error JSON contract.
    assertThat(response.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
  }
}
