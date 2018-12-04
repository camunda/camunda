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
package io.zeebe.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.util.StringUtil;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class CompleteJobTest extends ClientTest {

  @Test
  public void shouldCompleteJob() {
    // given
    final long jobKey = 12;

    // when
    client.newCompleteCommand(jobKey).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    assertThat(request.getPayload()).isEmpty();
  }

  @Test
  public void shouldCompleteWithJsonStringPayload() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client.newCompleteCommand(jobKey).payload(json).send().join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getPayload(), json);
  }

  @Test
  public void shouldCompleteWithJsonStreamPayload() {
    // given
    final long jobKey = 12;
    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    client
        .newCompleteCommand(jobKey)
        .payload(new ByteArrayInputStream(StringUtil.getBytes(json)))
        .send()
        .join();

    // then
    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getPayload(), json);
  }

  @Test
  public void shouldCompleteWithJsonMapPayload() {
    // given
    final long jobKey = 12;
    final Map<String, Object> map = Collections.singletonMap("key", "val");

    // when
    client.newCompleteCommand(jobKey).payload(map).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(map);

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getPayload(), expectedJson);
  }

  @Test
  public void shouldCompleteWithJsonPOJOPayload() {

    // given
    final long jobKey = 12;
    final POJO pojo = new POJO();
    pojo.setKey("val");

    // when
    client.newCompleteCommand(jobKey).payload(pojo).send().join();

    // then
    final String expectedJson = JsonUtil.toJson(pojo);

    final CompleteJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobKey()).isEqualTo(jobKey);
    JsonUtil.assertEquality(request.getPayload(), expectedJson);
  }

  public static class POJO {
    private String key;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }
  }
}
