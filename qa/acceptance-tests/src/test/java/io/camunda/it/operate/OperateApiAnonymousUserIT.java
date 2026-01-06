/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.util.Either;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateApiAnonymousUserIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withUnauthenticatedAccess();

  private static long processInstanceKey;

  @BeforeAll
  public static void beforeAll(final CamundaClient client) throws Exception {
    TestHelper.deployResource(client, "process/service_tasks_v1.bpmn");

    processInstanceKey =
        TestHelper.startProcessInstance(client, "service_tasks_v1").getProcessInstanceKey();
    TestHelper.waitForProcessInstances(client, q -> q.processInstanceKey(processInstanceKey), 1);
  }

  @Test
  public void shouldReturnProcessInstanceViaV1Api() throws Exception {

    // given
    try (final var operateClient = STANDALONE_CAMUNDA.newOperateClient()) {
      // when
      final HttpResponse<String> searchResponse =
          operateClient.sendV1SearchRequest("v1/process-instances", "{}");

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final Either<Exception, Map> result = operateClient.mapResult(searchResponse, Map.class);

      assertThat(result.isRight()).isTrue();
      final Map<String, Object> responseBody = result.get();

      assertThat(responseBody).containsEntry("total", 1);

      final List<Map<String, Object>> processInstances =
          (List<Map<String, Object>>) responseBody.get("items");

      assertThat(processInstances).hasSize(1);

      final Map<String, Object> processInstance = processInstances.get(0);
      assertThat(processInstance).containsEntry("key", processInstanceKey);
    }
  }

  @Test
  public void shouldReturnProcessInstanceViaInternalApi() throws Exception {

    // given
    try (final var operateClient = STANDALONE_CAMUNDA.newOperateClient()) {

      // when
      final HttpResponse<String> searchResponse =
          operateClient.sendInternalSearchRequest(
              "api/process-instances", "{\"query\": {\"active\": true, \"running\": true}}");

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final Either<Exception, Map> result = operateClient.mapResult(searchResponse, Map.class);

      assertThat(result.isRight()).isTrue();
      final Map<String, Object> responseBody = result.get();

      assertThat(responseBody).containsEntry("totalCount", 1);

      final List<Map<String, Object>> processInstances =
          (List<Map<String, Object>>) responseBody.get("processInstances");

      assertThat(processInstances).hasSize(1);

      final Map<String, Object> processInstance = processInstances.get(0);
      assertThat(processInstance).containsEntry("id", Long.toString(processInstanceKey));
    }
  }
}
