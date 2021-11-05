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
package io.camunda.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.client.util.JsonUtil;
import io.camunda.zeebe.client.util.StringUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.mockito.Mockito;

public final class CompleteJobTest extends ClientTest {

  @Test
  public void shouldCompleteJobByKey() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getVariables()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteJob() {
    // given
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newCompleteCommand(job).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(job.getKey());
    assertThat(request.getVariables()).isEmpty();

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCompleteWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client.newCompleteCommand(jobKey).variables(json).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), json);
  }

  @Test
  public void shouldCompleteWithJsonStreamVariables() {
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
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), json);
  }

  @Test
  public void shouldCompleteWithJsonMapVariables() {
    // given
    final long jobKey = 12;
    final Map<String, Object> map = Collections.singletonMap("key", "val");

    // when
    client.newCompleteCommand(jobKey).variables(map).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(map);

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), expectedJson);
  }

  @Test
  public void shouldCompleteWithJsonPOJOVariables() {

    // given
    final long jobKey = 12;
    final POJO pojo = new POJO();
    pojo.setKey("val");

    // when
    client.newCompleteCommand(jobKey).variables(pojo).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(pojo);

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getVariables(), expectedJson);
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newCompleteCommand(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  public static class POJO {
    private String key;

    public String getKey() {
      return key;
    }

    public void setKey(final String key) {
      this.key = key;
    }
  }
}
