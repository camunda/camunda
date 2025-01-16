/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.job.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.job.CompleteJobTest;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.JsonUtil;
import io.camunda.zeebe.client.util.StringUtil;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CompleteJobRestTest extends ClientRestTest {

  @Test
  void shouldCompleteJobByKey() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getVariables()).isNull();
  }

  @Test
  void shouldCompleteJob() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newCompleteCommand(job).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    assertThat(request.getVariables()).isNull();
  }

  @Test
  void shouldCompleteWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client.newCompleteCommand(jobKey).variables(json).send().join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), json);
  }

  @Test
  void shouldCompleteWithJsonStreamVariables() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client
        .newCompleteCommand(jobKey)
        .variables(new ByteArrayInputStream(StringUtil.getBytes(json)))
        .send()
        .join();

    // then
    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), json);
  }

  @Test
  void shouldCompleteWithJsonMapVariables() {
    // given
    final long jobKey = 12;
    final Map<String, Object> map = Collections.singletonMap("key", "val");

    // when
    client.newCompleteCommand(jobKey).variables(map).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(map);

    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), expectedJson);
  }

  @Test
  void shouldCompleteWithJsonPOJOVariables() {

    // given
    final long jobKey = 12;
    final CompleteJobTest.POJO pojo = new CompleteJobTest.POJO();
    pojo.setKey("val");

    // when
    client.newCompleteCommand(jobKey).variables(pojo).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(pojo);

    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), expectedJson);
  }

  @Test
  void shouldCompleteWithSingleVariable() {

    // given
    final long jobKey = 12;
    final String key = "key";
    final String value = "value";

    // when
    client.newCompleteCommand(jobKey).variable(key, value).send().join();

    // then
    final String expectedVariable = JsonUtil.toJson(Collections.singletonMap(key, value));

    final JobCompletionRequest request = gatewayService.getLastRequest(JobCompletionRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), expectedVariable);
  }
}
